# facial-evaluation
 Facial deviation value evaluation using scala on aws of serverless.
 
 ## Usage
 
 `sbt -mem 2048 -DAWS_ACCOUNT_ID=hoge -DAWS_ROLE_ARN=arn:aws:iam::hoge:role/role-name -DAWS_BUCKET_NAME=where lambda jar file upload`
 
 ## invoke etc
 `aws lambda invoke --region us-east-1 --function-name FacialEvaluation --payload 123 --invocation-type RequestResponse /tmp/response`
