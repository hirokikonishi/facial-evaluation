import java.nio.ByteBuffer

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.rekognition.AmazonRekognitionClient
import com.amazonaws.services.rekognition.model.{ CompareFacesRequest, Image }
import com.amazonaws.services.s3.model.{ GetObjectRequest, ListBucketsRequest, S3Object }
import com.amazonaws.services.s3.AmazonS3Client
import spray.json.JsonParser
import com.typesafe.config.ConfigFactory
import java.nio.ByteBuffer

import scala.collection.JavaConverters._

class FacialEvaluation {
}
object Main extends App {

  def listS3(): Unit = {
    val conf = ConfigFactory.load

    val s3 = new AmazonS3Client(new ProfileCredentialsProvider())
    val rekognition = new AmazonRekognitionClient(new ProfileCredentialsProvider())

    val bucketName = conf.getString("s3.bucket")
    s3.listObjects(bucketName).getObjectSummaries.asScala.map { r =>
      {
        val data = s3.getObject(bucketName, r.getKey)
        val s3ForRekognition = new com.amazonaws.services.rekognition.model.S3Object
        s3ForRekognition.withBucket(bucketName).withName(r.getKey).withVersion(data.getObjectMetadata.getVersionId)
        val sourceImage = new Image withS3Object s3ForRekognition

        val targetImage = new Image withS3Object s3ForRekognition

        println(r.getKey)
        println(sourceImage)
        println(targetImage)

        val compareReq = new CompareFacesRequest()
        compareReq.setSourceImage(sourceImage)
        compareReq.setTargetImage(targetImage)
        compareReq.setSimilarityThreshold(0.65f)
        println(compareReq)

        val req = rekognition.compareFaces(compareReq)
        println(req)
      }
    }
  }

  listS3()
}
