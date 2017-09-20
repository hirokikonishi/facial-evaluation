import java.time.format.DateTimeFormatter
import java.time.{ LocalDateTime, ZoneOffset, ZonedDateTime }

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.rekognition.{ AmazonRekognition, AmazonRekognitionClient, AmazonRekognitionClientBuilder }
import com.amazonaws.services.rekognition.model._
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.AmazonS3Client
import com.typesafe.config.{ Config, ConfigFactory }
import com.amazonaws.regions.Regions
import com.amazonaws.services.lambda.runtime.Context
import jp.co.bizreach.elasticsearch4s._
import org.codelibs.elasticsearch.common.rounding.DateTimeUnit

import collection.JavaConverters._
import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }

case class SystemError(cause: Option[Throwable])

trait Base {

  def createS3Client: AmazonS3Client = {
    val s3 = new AmazonS3Client()
    s3.withRegion(Regions.US_WEST_2)
    s3
  }

  def createRekognitionClient: AmazonRekognitionClient = {
    val rekognition = new AmazonRekognitionClient
    rekognition.withRegion(Regions.US_WEST_2)
    rekognition
  }

  def listS3(conf: Config): List[S3ObjectSummary] = {
    val s3 = createS3Client
    val bucketName = conf.getString("s3.bucket")

    s3.listObjects(bucketName).getObjectSummaries.asScala.toList
  }

  def getImage(bucket: String, key: String) = {
    val image = new Image()
    image.withS3Object(new com.amazonaws.services.rekognition.model.S3Object().withBucket(bucket).withName(key))
  }

  def callFaceScoring(source: Image): List[Label] = {
    val maxLabels: Int = 10
    val minConfidence = 80F
    val rekognition = createRekognitionClient

    val detectLabelsRequest = new DetectLabelsRequest()
    detectLabelsRequest
      .withImage(source)
      .withMaxLabels(maxLabels)
      .withMinConfidence(minConfidence)

    rekognition.detectLabels(detectLabelsRequest).getLabels.asScala.toList
  }

  def callCompareFaces(source: Image, target: Image): List[CompareFacesMatch] = {
    val similarityThreshould = 70F
    val rekognition = createRekognitionClient

    val compareFacesRequest = new CompareFacesRequest()
    compareFacesRequest
      .withSourceImage(source)
      .withTargetImage(target)
      .withSimilarityThreshold(similarityThreshould)

    rekognition.compareFaces(compareFacesRequest).getFaceMatches.asScala.toList
  }

  case class SmileScore(imageName: String, score: Float, dateTime: String)

  def insertByESClient(documentId: String, score: SmileScore) = {
    // Call this method once before using ESClient
    ESClient.init()
    ESClient.using("http://es_endpoint") { client =>
      //index / type
      val config = "smile" / "score"
      client.insert(config, documentId, score)
    }
    // Call this method before shutting down application
    ESClient.shutdown()
  }

  val conf = ConfigFactory.load

  /* compare request------------------------
  val sourceImage = getImage("bucketName", "keyName")
  for {
    s3Object <- listS3(profile, conf)
    compareTargetImage = getImage(conf.getString("s3.bucket"), s3Object.getKey)
    compareRes = callCompareFaces(profile, sourceImage, compareTargetImage)
    faceMatch <- compareRes
    _ = println(s"It's high score!! imageName: ${s3Object.getKey}, similarity: ${faceMatch.getSimilarity}")
  } yield ()

  ----------------------------------------*/

  def hundler(input: Any, context: Context) = {
    for {
      s3Object <- listS3(conf)
      labelSourceImage = getImage(conf.getString("s3.bucket"), s3Object.getKey)
      label <- callFaceScoring(labelSourceImage)
      _ = label.getName match {
        case "Smile" => {
          println(s"It's High Score   Smile Image: ${s3Object.getKey}, LabelName: ${label.getName}, Score: ${label.getConfidence.toString}")
          insertByESClient(
            documentId = s3Object.getKey,
            SmileScore(
              imageName = s3Object.getKey,
              score = label.getConfidence,
              dateTime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
            )
          )
        }
        case _ => ()
      }
    } yield ()
  }
}

class App extends Base
