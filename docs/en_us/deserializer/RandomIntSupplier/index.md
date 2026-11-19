## [RandomIntSupplier](../)

A random integer source.

### `rml:default_constant`

Default deserializer. The field is an [Integer](../Integer).

Example: `1`. That supplier always returns `1`.

### `minecraft:price`

#### Fields

`min` [Integer](../Integer)

`max` [Integer](../Integer)

Picks a value in `[min, max]`, using vanilla villager price logic. `min` must not be greater than `max`.

### `cvh:constant`

#### Fields

`constant` [Integer](../Integer)

### `cvh:poisson_distribution`

#### Fields

`min` [Integer](../Integer)

`max` [Integer](../Integer)

`lambda` [Integer](../Integer)

Samples a Poisson distribution, then remaps the result into `[min, max]`.
