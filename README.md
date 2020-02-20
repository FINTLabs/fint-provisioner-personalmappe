# FINT Provisioner Personalmappe

![flyt-overordnet](diagrams/flyt-overordnet-light.png)

## Provisioning Service flow
![flyt-provisioning-service-create](diagrams/flyt-provisjoneringstjeneste-create.png)
![flyt-provisioning-service-update](diagrams/flyt-provisjoneringstjeneste-update.png)

## Adapter flow
![flow-adapter-create](diagrams/flyt-adapter-create.png)
![flow-adapter-update](diagrams/flyt-adapter-update.png)

# Configuration
> `orgId` should be replaced by the organisations orgId and the `.` should be replaced with a dash `-`. I.e. `viken.no` should be `viken-no`.

| Key                                                                                                                | Description | Default value                                                          |
| ------------------------------------------------------------------------------------------------------------------ | ----------- | ---------------------------------------------------------------------- |
| fint.endpoints.personalressurs                                                                                     |             | https://api.felleskomponent.no/administrasjon/personal/personalressurs |
| fint.endpoints.personalmappe                                                                                       |             | https://alpha.felleskomponent.no/administrasjon/personal/personalmappe |
| fint.endpoints.graphql                                                                                             |             | https://api.felleskomponent.no/graphql/graphql                         |
| fint.cron.bulk                                                                                           |             | 0 0 0 * * MON-FRI                                                      |
| fint.cron.bulk.delta                                                                                     |             | 0 */5 8-16 * * MON-FRI                                                 |
| fint.organisations.`<orgId>`.registration                                                                  |             |                                                                        |
| fint.organisations.`<orgId>`.username                                                         |             |                                                                        |
| fint.organisations.`<orgId>`.password                                                |             |                                                                        |
| fint.organisations.`<orgId>`.bulk-limit                                              |             | 5                                                                      |
| fint.organisations.`orgId>`.bulk                                                    |             | false                                                                  |
| fint.organisations.`<orgId>`.delta                                                   |             | true                                                                   |
| spring.security.oauth2.client.registration.`<orgId>`.client-id                                                       |             |                                                                        |
| spring.security.oauth2.client.registration.`<orgId>`.client-secret                                         |             |                                                                        |
| spring.security.oauth2.client.registration.`<orgId>`.authorization-grant-type                |             | password                                                               |
| spring.security.oauth2.client.registration.`<orgId>`.scope          |             | fint-client                                                            |
| spring.security.oauth2.client.registration.`<orgId>`.provider |             | fint                                                                   |
| spring.security.oauth2.client.provider.fint.token-uri                 |             | https://idp.felleskomponent.no/nidp/oauth/nam/token                    |
| spring.security.data.mongodb.uri                                                                                   |             |                                                                        |
| server.servlet.context-path                                                                                        |             | /tjenester/personalmappe                                               |
