# Add to build.gradle

# implementation 'io.quarkus:quarkus-azure-key-vault'

# application.yaml

quarkus:
azure:
keyvault:
url: https://your-vault.vault.azure.net/
tenant-id: ${AZURE_TENANT_ID}
client-id: ${AZURE_CLIENT_ID}
client-secret: ${AZURE_CLIENT_SECRET}

# Reference secrets:

mp:
messaging:
connector:
smallrye-amqp:
username: ${kv//amqp-username}
password: ${kv//amqp-password}
