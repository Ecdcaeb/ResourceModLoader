### 范围（ValueGetter）

如果没有type字段，则默认type为"minecraft:price"。


#### 常数
注册名`cvh:constant`

example
```json
{
    "type":"cvh:constant",
    "constant":1
}
```
#### 均匀取值
注册名`minecraft:price`

example
```json
{
  "type":"minecraft:price",
  "min": 1,
  "max": 2
}
```
#### 柏松分布
注册名`cvh:poisson_distribution`
被限制，柏松分布，但是取值在[min,max)内。

example
```json
{
  "type":"cvh:poisson_distribution",
  "min": 1,
  "max": 22,
  "lambda":5
}
```