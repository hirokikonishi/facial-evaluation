import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.rekognition.{ AmazonRekognition, AmazonRekognitionClient, AmazonRekognitionClientBuilder }
import com.amazonaws.services.rekognition.model.{ CompareFacesMatch, CompareFacesRequest, CompareFacesResult, Image }
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
        val sourceImage = getImage(bucketName, r.getKey)
        val targetImage = getImage(bucketName, r.getKey)
        val similarityThreshould = 70F

        val res = callCompareFaces(sourceImage, targetImage, similarityThreshould, rekognition)
        println(res)

        res.getFaceMatches match {
          case c: CompareFacesMatch => println(c.getFace)
          case x => println(s"x= ${x}")
        }
      }
    }
  }
  listS3()
}
