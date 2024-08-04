## Deserializer

`Deserializer` is a tool designed by rml and used to deserialize Json to the target object.

`rml.loader.deserialize.Deserializer` is a deserializer used in the RML environment. If you develop based on RML, you should use it.

`rml.deserializer.DeserializerBuilder` is a parser builder used for JsonObject.

To parse an object, you can use `rml.loader.deserialize.Deserializer#decode`

Deserializer does not support generics.

`rml.deserializer.Argument` is a very distinctive parameter type built for `DeserializerBuilder`.