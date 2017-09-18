import java.time.format.DateTimeFormatter
import java.time.{ LocalDateTime, ZoneOffset, ZonedDateTime }

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.rekognition.{ AmazonRekognition, AmazonRekognitionClient, AmazonRekognitionClientBuilder }
import com.amazonaws.services.rekognition.model._
import com.amazonaws.services.s3.model.{ GetObjectRequest, ListBucketsRequest, S3Object }
import com.amazonaws.services.s3.AmazonS3Client
import com.typesafe.config.ConfigFactory
import com.amazonaws.regions.Regions
import jp.co.bizreach.elasticsearch4s._
import org.codelibs.elasticsearch.common.rounding.DateTimeUnit

import scala.collection.JavaConverters._

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

  def getImage(bucket: String, key: String) = {
    val image = new Image()
    image.withS3Object(new com.amazonaws.services.rekognition.model.S3Object().withBucket(bucket).withName(key))
  }

  def callFaceScoring(source: Image, maxLabels: Int, minConfidence: Float, rekognition: AmazonRekognition): DetectLabelsResult = {
    val detectLabelsRequest = new DetectLabelsRequest()
    detectLabelsRequest
      .withImage(source)
      .withMaxLabels(maxLabels)
      .withMinConfidence(minConfidence)

    rekognition.detectLabels(detectLabelsRequest)
  }

  def callCompareFaces(source: Image, target: Image, similarityThreshould: Float, rekognition: AmazonRekognition): CompareFacesResult = {
    val compareFacesRequest = new CompareFacesRequest()
    compareFacesRequest
      .withSourceImage(source)
      .withTargetImage(target)
      .withSimilarityThreshold(similarityThreshould)

    rekognition.compareFaces(compareFacesRequest)
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
        val labelSourceImage = getImage(bucketName, r.getKey)
        val labelMaxLabels: Int = 10
        val labelMinConfidence = 80F
        val labelRes = callFaceScoring(labelSourceImage, labelMaxLabels, labelMinConfidence, rekognition)
        val labels = labelRes.getLabels
        labels.asScala.toList.map { l =>
          l.getName match {
            case "Smile" => {
              println(s"It's High Score   Smile Image: ${r.getKey}, LabelName: ${l.getName}, Score: ${l.getConfidence.toString}")
              insertByESClient(
                documentId = r.getKey,
                SmileScore(
                  imageName = r.getKey,
                  score = l.getConfidence,
                  dateTime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
                )
              )
            }
            case _ => ()
          }
        }
        println(s"labels: ${labels}")

        /* ---------------------------------------------------------------------- */

        /* compare request */
        val compareSourceImage = getImage(bucketName, r.getKey)
        val compareTargetImage = getImage(bucketName, r.getKey)
        val compareSimilarityThreshould = 70F

        //val compareRes = callCompareFaces(compareSourceImage, compareTargetImage, compareSimilarityThreshould, rekognition)
        //compareRes.getFaceMatches

        /* ---------------------------------------------------------------------- */
      }
    }
  }
  facialEvaliation()
}
