# Add to build.gradle

# implementation 'io.quarkus:quarkus-amazon-secretsmanager'

# application.yaml

quarkus:
secretsmanager:
enabled: true
endpoint-override: # optional for testing
region: us-east-1
secrets:
amqp-credentials:
secret-id: prod/signal-processor/amqp

# Reference in config:

mp:
messaging:
connector:
smallrye-amqp:
username: ${amqp-credentials.username}
password: ${amqp-credentials.password}

# In AWS Secrets Manager, create secret with:

# {

# "username": "actual-username",

# "password": "actual-password"

# }
