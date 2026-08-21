## Deserializer

`Deserializer` turns JSON into typed Java objects.

- `rml.loader.deserialize.Deserializer` is the manager used inside RML. Use it if you depend on RML.
- `rml.deserializer.DeserializerBuilder` builds object parsers for `JsonObject`.
- `rml.loader.deserialize.Deserializer#decode` decodes one value.
- Generics are not supported.
- `rml.deserializer.Argument` is a named field action for `DeserializerBuilder`.
