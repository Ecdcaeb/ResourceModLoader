## [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/)

A random integer source.

### `rml:default_constant`

Default deserializer. The field is an [Integer](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/Integer).

Example: `1`. That supplier always returns `1`.

### `minecraft:price`

#### Fields

`min` [Integer](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/Integer)

`max` [Integer](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/Integer)

Picks a value in `[min, max]`, using vanilla villager price logic. `min` must not be greater than `max`.

### `cvh:constant`

#### Fields

`constant` [Integer](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/Integer)

### `cvh:poisson_distribution`

#### Fields

`min` [Integer](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/Integer)

`max` [Integer](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/Integer)

`lambda` [Integer](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/Integer)

Samples a Poisson distribution, then remaps the result into `[min, max]`.
