import java.time.format.DateTimeFormatter
import java.time.{ LocalDateTime, ZoneOffset, ZonedDateTime }

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.rekognition.{ AmazonRekognition, AmazonRekognitionClient, AmazonRekognitionClientBuilder }
import com.amazonaws.services.rekognition.model._
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.AmazonS3Client
import com.typesafe.config.{ Config, ConfigFactory }
import com.amazonaws.regions.Regions
import jp.co.bizreach.elasticsearch4s._
import org.codelibs.elasticsearch.common.rounding.DateTimeUnit
import collection.JavaConverters._

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }

case class SystemError(cause: Option[Throwable])

object SystemError {
  def apply(): SystemError = new SystemError(None)
  def apply(cause: Throwable): SystemError = new SystemError(Some(cause))
}

class FacialEvaluation {
}
object Main extends App {

  def createS3Client(profile: ProfileCredentialsProvider): AmazonS3Client = {
    val s3 = new AmazonS3Client(profile)
    s3.withRegion(Regions.US_WEST_2)
    s3
  }

  def createRekognitionClient(profile: ProfileCredentialsProvider): AmazonRekognitionClient = {
    val rekognition = new AmazonRekognitionClient(profile)
    rekognition.withRegion(Regions.US_WEST_2)
    rekognition
  }

  def try2either[A](f: => Try[A]): Either[SystemError, A] = {
    f match {
      case Success(s) => Right(s)
      case Failure(e) => Left(SystemError(e))
    }
  }

  def listS3(profile: ProfileCredentialsProvider, conf: Config): List[S3ObjectSummary] = {
    val s3 = createS3Client(profile)
    val bucketName = conf.getString("s3.bucket")

    s3.listObjects(bucketName).getObjectSummaries.asScala.toList
  }

  def getImage(bucket: String, key: String) = {
    val image = new Image()
    image.withS3Object(new com.amazonaws.services.rekognition.model.S3Object().withBucket(bucket).withName(key))
  }

  def callFaceScoring(profile: ProfileCredentialsProvider, source: Image): List[Label] = {
    val maxLabels: Int = 10
    val minConfidence = 80F
    val rekognition = createRekognitionClient(profile)

    val detectLabelsRequest = new DetectLabelsRequest()
    detectLabelsRequest
      .withImage(source)
      .withMaxLabels(maxLabels)
      .withMinConfidence(minConfidence)

    rekognition.detectLabels(detectLabelsRequest).getLabels.asScala.toList
  }

  def callCompareFaces(profile: ProfileCredentialsProvider, source: Image, target: Image): List[CompareFacesMatch] = {
    val similarityThreshould = 70F
    val rekognition = createRekognitionClient(profile)

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
    ESClient.using("http://localhost:9200") { client =>
      //index / type
      val config = "smile" / "score"
      client.insert(config, documentId, score)
    }
    // Call this method before shutting down application
    ESClient.shutdown()
  }

  def facialEvaliation(): Unit = {
    val conf = ConfigFactory.load
    val profile = new ProfileCredentialsProvider()

    val s3 = createS3Client(profile)
    val rekognition = createRekognitionClient(profile)

    val bucketName = conf.getString("s3.bucket")
    s3.listObjects(bucketName).getObjectSummaries.asScala.map { r =>
      {
        /* label request */
        //val labels = callFaceScoring(profile, labelSourceImage).getLabeles
        //println(s"labels: ${labels}")

        /* ---------------------------------------------------------------------- */

        /* compare request */
        //val compareSourceImage = getImage(bucketName, r.getKey)
        //val compareTargetImage = getImage(bucketName, r.getKey)
        //val compareSimilarityThreshould = 70F

        //val compareRes = callCompareFaces(compareSourceImage, compareTargetImage, compareSimilarityThreshould, rekognition)
        //compareRes.getFaceMatches

        /* ---------------------------------------------------------------------- */
      }
    }
  }

  val conf = ConfigFactory.load
  val profile = new ProfileCredentialsProvider()

  for {
    s3Object <- listS3(profile, conf)
    labelSourceImage = getImage(conf.getString("s3.bucket"), s3Object.getKey)
    label <- callFaceScoring(profile, labelSourceImage)
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
