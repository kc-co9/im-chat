# Plugin Module Guide

## Ownership

Plugin modules package reusable infrastructure capabilities. They may depend on `im-common` and third-party libraries, but never on Broker, Gateway, management, or business-service implementation packages.

## Boundaries

- Keep auto-configuration classes at the plugin root unless an established plugin pattern says otherwise.
- Put configuration binding types under `properties` and use typed properties instead of scattered `@Value` fields.
- Auto-configuration must back off when the application supplies an equivalent bean.
- A plugin resource imported automatically must provide safe local defaults and remain overridable by the consuming application and Nacos.
- SPI and core model types must not expose third-party implementation objects unless the plugin explicitly wraps that technology.
- Generic synchronization logic belongs in `im-gossip`; Broker-specific entity interpretation remains in Broker.

## Verification

For plugin changes run the plugin tests and:

```bash
./scripts/verify.sh architecture
```
