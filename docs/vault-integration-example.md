# Add to build.gradle dependencies

# implementation 'io.quarkus:quarkus-vault'

# application.yaml for Vault

quarkus:
vault:
url: https://vault.company.com:8200
authentication:
kubernetes:
role: signal-processor
secret-config-kv-path: secret/signal-processor

# Then reference like this:

mp:
messaging:
connector:
smallrye-amqp:
username: ${amqp.username}
password: ${amqp.password}

# Vault stores the secrets at: secret/signal-processor

# {

# "amqp": {

# "username": "actual-username",

# "password": "actual-password"

# }

# }
