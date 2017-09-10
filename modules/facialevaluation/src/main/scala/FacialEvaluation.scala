import java.nio.ByteBuffer

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.rekognition.AmazonRekognitionClient
import com.amazonaws.services.rekognition.model.{ CompareFacesRequest, Image }
import com.amazonaws.services.s3.model.{ GetObjectRequest, ListBucketsRequest, S3Object }
import com.amazonaws.services.s3.AmazonS3Client
import spray.json.JsonParser
import com.typesafe.config.ConfigFactory

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
        val aaa = new com.amazonaws.services.rekognition.model.S3Object
        aaa.withBucket(bucketName).withName(r.getKey)
        val sourceImage = new Image()
        sourceImage.setS3Object(aaa)
        val compareReq = new CompareFacesRequest()

        val targetImage = new Image()
        targetImage.setS3Object(aaa)
        println(r.getKey)
        println(sourceImage)
        println(targetImage)

        compareReq.setSourceImage(sourceImage)
        compareReq.setTargetImage(targetImage)
        println(compareReq)

        val req = rekognition.compareFaces(compareReq)
        println(req)
      }
    }
  }

  listS3()
}
