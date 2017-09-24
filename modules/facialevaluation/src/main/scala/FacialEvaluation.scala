import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import com.amazonaws.regions.Regions
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.rekognition.AmazonRekognitionClient
import com.amazonaws.services.rekognition.model._
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.bizreach.elasticsearch4s._

import scala.collection.JavaConverters._
import scala.util.Try

case class SystemError(cause: Option[Throwable])

trait domain {
  case class SmileScore(imageName: String, score: Float, dateTime: String)

  def listS3(conf: Config): Try[List[S3ObjectSummary]]
  def getImage(bucket: String, key: String): Try[Image]
  def callDetectLabels(source: Image): Try[List[Label]]
  def callDetectFaces(source: Image): Try[DetectFacesResult]
  def callCompareFaces(source: Image, target: Image): Try[List[CompareFacesMatch]]
  def insertByESClient(config: ESConfig, documentId: String, score: SmileScore): Try[Unit]
}

trait infrastruture extends domain {

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

  override def listS3(conf: Config): Try[List[S3ObjectSummary]] = Try {
    val s3 = createS3Client
    val bucketName = conf.getString("s3.bucket")

    s3.listObjects(bucketName).getObjectSummaries.asScala.toList
  }

  override def getImage(bucket: String, key: String): Try[Image] = Try {
    val image = new Image()
    image.withS3Object(new com.amazonaws.services.rekognition.model.S3Object().withBucket(bucket).withName(key))
  }

  override def callDetectLabels(source: Image): Try[List[Label]] = Try {
    val maxLabels: Int = 20
    val minConfidence = 10F
    val rekognition = createRekognitionClient

    val detectLabelsRequest = new DetectLabelsRequest()
    detectLabelsRequest
      .withImage(source)
      .withMaxLabels(maxLabels)
      .withMinConfidence(minConfidence)

    rekognition.detectLabels(detectLabelsRequest).getLabels.asScala.toList
  }

  override def callDetectFaces(source: Image): Try[DetectFacesResult] = Try {
    val rekognition = createRekognitionClient

    val detectFacesRequest = new DetectFacesRequest
    detectFacesRequest.withImage(source).withAttributes("All")
    rekognition.detectFaces(detectFacesRequest)
  }

  override def callCompareFaces(source: Image, target: Image): Try[List[CompareFacesMatch]] = Try {
    val similarityThreshould = 20F
    val rekognition = createRekognitionClient

    val compareFacesRequest = new CompareFacesRequest()
    compareFacesRequest
      .withSourceImage(source)
      .withTargetImage(target)
      .withSimilarityThreshold(similarityThreshould)

    rekognition.compareFaces(compareFacesRequest).getFaceMatches.asScala.toList
  }

  override def insertByESClient(config: ESConfig, documentId: String, score: SmileScore): Try[Unit] = Try {
    // Call this method once before using ESClient
    ESClient.init()
    ESClient.using("http://es.endpoint") { client =>
      client.insert(config, documentId, score)
    }
    // Call this method before shutting down application
    ESClient.shutdown()
  }
}

trait Base extends domain with infrastruture {

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

    /* detectFaces */
    for {
      s3Objects <- listS3(conf)
    } yield for {
      s3Object <- s3Objects
    } yield for {
      labelSourceImage <- getImage(conf.getString("s3.bucket"), s3Object.getKey)
      faceDetectDetails <- callDetectFaces(labelSourceImage)
    } yield for {
      faceDetail <- faceDetectDetails.getFaceDetails.asScala.toList
      emotion <- faceDetail.getEmotions.asScala.toList
      _ = println(s"It's High HAPPY Score!! Happy Image: ${s3Object.getKey}, emotion: ${emotion.toString}")
      //index / type
      config = "happy" / "score"
      _ = insertByESClient(
        config = config,
        documentId = s3Object.getKey,
        SmileScore(
          imageName = s3Object.getKey,
          score = emotion.getConfidence,
          dateTime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        )
      )
    } yield ()

    /* detectLabels*/
    for {
      s3Objects <- listS3(conf)
    } yield for {
      s3Object <- s3Objects
    } yield for {
      labelSourceImage <- getImage(conf.getString("s3.bucket"), s3Object.getKey)
      labels <- callDetectLabels(labelSourceImage)
    } yield for {
      label <- labels
      _ = label.getName match {
        // 笑顔度合い
        case "Smile" => {
          println(s"It's High Smile Score!! Smile Image: ${s3Object.getKey}, LabelName: ${label.getName}, Score: ${label.getConfidence.toString}")
          //index / type
          val config = "smile" / "score"
          insertByESClient(
            config = config,
            documentId = s3Object.getKey,
            SmileScore(
              imageName = s3Object.getKey,
              score = label.getConfidence,
              dateTime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
            )
          )
        }
        // 幸せ度合い
        case "HAPPY" => {
          println(s"It's High HAPPY Score!! HAPPY Image: ${s3Object.getKey}, LabelName: ${label.getName}, Score: ${label.getConfidence.toString}")
          //index / type
          val config = "happy" / "score"
          insertByESClient(
            config = config,
            documentId = s3Object.getKey,
            SmileScore(
              imageName = s3Object.getKey,
              score = label.getConfidence,
              dateTime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
            )
          )
        }
        case "SAD" => {
          println(s"IT's SAD Face.... SAD Image ${s3Object.getKey}, LableName: ${label.getName}, Score: ${label.getConfidence.toString}")
          val config = "sad" / "score"
          insertByESClient(
            config = config,
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
