# FINT Personalmappe Provisioning Service

- [FINT Personalmappe Provisioning Service](#fint-personalmappe-provisioning-service)
  - [Overall flow](#overall-flow)
  - [Provisioning Service flow](#provisioning-service-flow)
    - [On create](#on-create)
    - [On update](#on-update)
  - [Adapter flow](#adapter-flow)
    - [On create](#on-create-1)
    - [On update](#on-update-1)
- [Ecma transform policies](#ecma-transform-policies)
  - [The transformation function](#the-transformation-function)
    - [Examples](#examples)
  - [Resource helper](#resource-helper)
    - [Examples](#examples-1)
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

# Ecma transform policies
The service supports transform policies using Ecma script (Javascript) on the `Nashorn` engine.

## The transformation function
The transformation script needs to have this signature `function myTransformationFunc(object)`. The parameter is 
a `PersonalmappeResource` object which one can modify. The transformation function must return this object.

> You can access all the methods of the `PersonalmappeResource` object in the script.
> E.g. `personalmappeResource.setTittel('new title)

### Examples

This example returns a unmodified object.
```javascript
function simpleTransform(o) {
    return o;
}
``` 

This example returns a object with the titles modified.
```javascript
function titleTransform(o) {
    o.setTittel("New title");
    o.setOffentligTittel("New title");
    return o;
}
``` 

## Resource helper
We created a helper to make it easier to match and modify links. The `resource` helper takes the `PersonalmappeResource`
object as a parameter. It has the following methods:

* link(relation_name)
* id(identifikator_name)
* getLink()
* getValue()
* replaceValue()
* is(value)
* isNot(value)

### Examples
This example changes the `saksstatus` value to `E` if it is `B`.

```javascript
function transform(o) {
    var r = resource(o).link("saksstatus");
    if (r.is("B")) {
        r.replaceValue("E");
    }
    return o;
}
``` 


# Configuration
> `orgId` should be replaced by the organisations orgId and the `.` should be replaced with a dash `-`. I.e. `viken.no` should be `viken-no`.

| Key                                                                           | Description                                                                                                                                                           | Default value                                                          |
| ----------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| fint.endpoints.personalressurs                                                | `Personalressurs` endpoint.                                                                                                                                           | https://api.felleskomponent.no/administrasjon/personal/personalressurs |
| fint.endpoints.personalmappe                                                  | `Personalmappe` endpoint.                                                                                                                                             | https://alpha.felleskomponent.no/administrasjon/personal/personalmappe |
| fint.endpoints.graphql                                                        | `GraphQL` endpoint.                                                                                                                                                   | https://api.felleskomponent.no/graphql/graphql                         |
| fint.cron.bulk                                                                | Cron expression for full synchronisation                                                                                                                              | `0 0 0 * * MON-FRI`                                                    |
| fint.cron.bulk.delta                                                          | Cron expression for delta synchronisation                                                                                                                             | `0 */5 8-16 * * MON-FRI`                                               |
| fint.organisations.`<orgId>`.registration                                     | Should be the same as `<orgId>`. I.e. `viken-no`                                                                                                                      |                                                                        |
| fint.organisations.`<orgId>`.username                                         | `username` for API user from the customer portal.                                                                                                                     |                                                                        |
| fint.organisations.`<orgId>`.password                                         | `password` for API user from the customer portal.                                                                                                                     |                                                                        |
| fint.organisations.`<orgId>`.personalressurskategori                          | List of `personalressurskategorier` that will be provisioned.                                                                                                         | `F` and `M`                                                            |
| fint.organisations.`<orgId>`.bulk-limit                                       | This is the number of `personalressurser` to synchronise on a load. If set to `0` all will be synchronised. Setting it to another value is meant for initial testing. | `5`                                                                    |
| fint.organisations.`orgId>`.bulk                                              | `true` or `false`. If `true` bulk synchronisation is enabled.                                                                                                         | `false`                                                                |
| fint.organisations.`<orgId>`.delta                                            | `true` or `false`. If `true` delta synchronisation is enabled.                                                                                                        | `false`                                                                 |
| fint.organisations.`<orgId>`.transformation-scripts                           | Javascripts to transform `PersonalmappeResource` object before sent to the archive system. See [Ecma transform policies](#ecma-transform-policies)                    ||
| fint.status-pending.fixed-backoff | Seconds to backoff on retries after creating/updating personalmappe. | 5 |
| fint.status-pending.max-retry | Max retries on status endpoint after creating/updating personalmappe. | 2 |
| spring.security.oauth2.client.registration.`<orgId>`.client-id                | `client-id` for API user from the customer portal.                                                                                                                    |                                                                        |
| spring.security.oauth2.client.registration.`<orgId>`.client-secret            | `client-secret` for API user from the customer portal.                                                                                                                |                                                                        |
| spring.security.oauth2.client.registration.`<orgId>`.authorization-grant-type | OAuth grant type. Should not be changed. Changing this will cause authentication not to work.                                                                         | `password`                                                             |
| spring.security.oauth2.client.registration.`<orgId>`.scope                    | OAuth scope.                                                                                                                                                          | `fint-client`                                                          |
| spring.security.oauth2.client.registration.`<orgId>`.provider                 | Should not be changed.                                                                                                                                                | `fint`                                                                 |
| spring.security.oauth2.client.provider.fint.token-uri                         | Token uri for the IDP.                                                                                                                                                | https://idp.felleskomponent.no/nidp/oauth/nam/token                    |
| spring.data.mongodb.uri                                                       | URI for the Mongo database.                                                                                                                                           |                                                                        |
| spring.data.mongodb.database                                                  | Name of the Mongo database.                                                                                                                                           |                                                                        |
| server.servlet.context-path                                                   | Base url for the service.                                                                                                                                             | `/tjenester/personalmappe`                                             |
