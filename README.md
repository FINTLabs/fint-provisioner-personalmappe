# FINT Personalmappe Provisioning Service

- [FINT Personalmappe Provisioning Service](#fint-personalmappe-provisioning-service)
  - [Overall flow](#overall-flow)
  - [Provisioning Service flow](#provisioning-service-flow)
    - [On create](#on-create)
    - [On update](#on-update)
  - [Adapter flow](#adapter-flow)
    - [On create](#on-create-1)
    - [On update](#on-update-1)
- [Configuration](#configuration)

This service provisions employee files in the archive system using FINT Core APIs.

The main features are:
- Create `personalmappe` for new employees or if it's not existing.
- Change `administrative enhet` and `saksbehandler` (leder) when it changes.
- Sets the case status to a configured value when the employeer leaves.
- If `administraiv enhet` or `saksbehandler` does not exist in the archive system the 
case is put on a configured `administraiv enhet` and `saksbehandler`.
- On a configured time there is a full syncronization of `personalmapper` once a day. [Default](#configuration) is at midnight.
- On configured intervals there is incremental updated of `personalmapper`. [Default](#configuration) is once an hour.

## Overall flow
![flyt-overordnet](diagrams/flyt-overordnet-light.png)

## Provisioning Service flow
### On create
![flyt-provisioning-service-create](diagrams/flyt-provisjoneringstjeneste-create.png)

### On update
![flyt-provisioning-service-update](diagrams/flyt-provisjoneringstjeneste-update.png)

## Adapter flow
### On create
![flow-adapter-create](diagrams/flyt-adapter-create.png)

### On update
![flow-adapter-update](diagrams/flyt-adapter-update.png)

# Configuration
> `orgId` should be replaced by the organisations orgId and the `.` should be replaced with a dash `-`. I.e. `viken.no` should be `viken-no`.

| Key                                                                           | Description | Default value                                                          |
| ----------------------------------------------------------------------------- | ----------- | ---------------------------------------------------------------------- |
| fint.endpoints.personalressurs                                                | `Personalressurs` endpoint.            | https://api.felleskomponent.no/administrasjon/personal/personalressurs |
| fint.endpoints.personalmappe                                                  | `Personalmappe` endpoint.            | https://alpha.felleskomponent.no/administrasjon/personal/personalmappe |
| fint.endpoints.graphql                                                        | `GraphQL` endpoint.            | https://api.felleskomponent.no/graphql/graphql                         |
| fint.cron.bulk                                                                | Cron expression for full synchronisation            | `0 0 0 * * MON-FRI`                                                      |
| fint.cron.bulk.delta                                                          | Cron expression for delta synchronisation             | `0 */5 8-16 * * MON-FRI`                                                 |
| fint.organisations.`<orgId>`.registration                                     | Should be the same as `<orgId>`. I.e. `viken-no`            |                                                                        |
| fint.organisations.`<orgId>`.username                                         | `username` for API user from the customer portal.            |                                                                        |
| fint.organisations.`<orgId>`.password                                         | `password` for API user from the customer portal.            |                                                                        |
| fint.organisations.`<orgId>`.personalressurskategori                                         | List of `personalressurskategorier` that will be provisioned.          | `F` and `M`                                                                       |
| fint.organisations.`<orgId>`.bulk-limit                                       | This is the number of `personalressurser` to synchronise on a load. If set to `0` all will be synchronised. Setting it to another value is meant for initial testing.            | `5`                                                                      |
| fint.organisations.`orgId>`.bulk                                              | `true` or `false`. If `true` bulk synchronisation is enabled.            | `false`                                                                  |
| fint.organisations.`<orgId>`.delta                                            | `true` or `false`. If `true` delta synchronisation is enabled.            | `true`                                                                   |
| spring.security.oauth2.client.registration.`<orgId>`.client-id                | `client-id` for API user from the customer portal.             |                                                                        |
| spring.security.oauth2.client.registration.`<orgId>`.client-secret            | `client-secret` for API user from the customer portal.             |                                                                        |
| spring.security.oauth2.client.registration.`<orgId>`.authorization-grant-type | OAuth grant type. Should not be changed. Changing this will cause authentication not to work.            | `password`                                                               |
| spring.security.oauth2.client.registration.`<orgId>`.scope                    | OAuth scope.            | `fint-client`                                                           |
| spring.security.oauth2.client.registration.`<orgId>`.provider                 | Should not be changed.            | `fint`                                                                   |
| spring.security.oauth2.client.provider.fint.token-uri                         | Token uri for the IDP.            | https://idp.felleskomponent.no/nidp/oauth/nam/token                    |
| spring.security.data.mongodb.uri                                              | URI for the Mongo database.            |                                                                        |
| server.servlet.context-path                                                   | Base url for the service.            | `/tjenester/personalmappe`                                               |
