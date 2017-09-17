import com.github.yoshiyoshifujii.aws.apigateway._

lazy val regionName = sys.props.getOrElse("AWS_REGION", "us-west-2")
lazy val accountId = sys.props.getOrElse("AWS_ACCOUNT_ID", "")
lazy val roleArn = sys.props.getOrElse("AWS_ROLE_ARN", "")
lazy val bucketName = sys.props.getOrElse("AWS_BUCKET_NAME", "")
lazy val authKey = sys.props.getOrElse("AUTH_KEY", "")

val commonSettings = Seq(
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.8",
  organization := "com.github.hirokikonishi.facial-evaluation"
)

val assemblySettings = Seq(
  assemblyMergeStrategy in assembly := {
    case "application.conf" => MergeStrategy.concat
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  },
  assemblyJarName in assembly := s"${name.value}-${version.value}.jar",
  publishArtifact in (Compile, packageBin) := false,
  publishArtifact in (Compile, packageSrc) := false,
  publishArtifact in (Compile, packageDoc) := false
)

val awsSettings = Seq(
  awsRegion := regionName,
  awsAccountId := accountId
)

val lambdaSettings = Seq(
  awsLambdaFunctionName := s"${name.value}",
  awsLambdaDescription := "Facial deviation value evaluation using scala on aws of serverless.",
  awsLambdaRole := roleArn,
  awsLambdaTimeout := 15,
  awsLambdaMemorySize := 1536,
  awsLambdaS3Bucket := bucketName,
  awsLambdaDeployDescription := s"${version.value}",
  awsLambdaAliasNames := Seq(
    "test", "production"
  )
)

lazy val root = (project in file(".")).
  aggregate(FacialEvaluation).
  settings(commonSettings: _*)

lazy val FacialEvaluation = (project in file("./modules/facialevaluation")).
  settings(commonSettings: _*).
  settings(assemblySettings: _*).
  settings(lambdaSettings: _*).
  settings(
    name := "FacialEvaluation",
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.2.1",
      "com.amazonaws" % "aws-lambda-java-core" % "1.1.0",
      "com.amazonaws" % "aws-java-sdk-s3" % "1.11.184",
      "com.amazonaws" % "aws-java-sdk-rekognition" % "1.11.101",
      "io.spray" %%  "spray-json" % "1.3.2",
      "jp.co.bizreach" %% "elastic-scala-httpclient" % "3.1.0"
    ),
    awsLambdaHandler := "com.hirokikonishi.FacialEvaluation::handleRequest"
  )
