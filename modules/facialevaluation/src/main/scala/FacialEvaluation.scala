import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.rekognition.{ AmazonRekognition, AmazonRekognitionClient, AmazonRekognitionClientBuilder }
import com.amazonaws.services.rekognition.model._
import com.amazonaws.services.s3.model.{ GetObjectRequest, ListBucketsRequest, S3Object }
import com.amazonaws.services.s3.AmazonS3Client
import com.typesafe.config.ConfigFactory
import com.amazonaws.regions.Regions

import scala.collection.JavaConverters._

class FacialEvaluation {
}
object Main extends App {

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

  def listS3(): Unit = {
    val conf = ConfigFactory.load
    val profile = new ProfileCredentialsProvider()

    val s3 = new AmazonS3Client(profile)
    val rekognition = new AmazonRekognitionClient(profile)
    rekognition.withRegion(Regions.US_WEST_2)

    val bucketName = conf.getString("s3.bucket")
    s3.listObjects(bucketName).getObjectSummaries.asScala.map { r =>
      {
        /* label request */
        val labelSourceImage = getImage(bucketName, r.getKey)
        val labelMaxLabels: Int = 10
        val labelMinConfidence = 70F
        //val labelRes = callFaceScoring(labelSourceImage, labelMaxLabels, labelMinConfidence, rekognition)
        //labelRes.getLabels

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
  listS3()
}
