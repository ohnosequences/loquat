# Parametric input/output types

A little recap for how loquat works. The idea is that you define a way of turning an input into an output, represented as

- records (their types) of data (*datasets* in our terminology)
- and their denotations: the values, as data locations

These data locations can be local files or S3 objects in our case. For our work with projects, we need to escape from the compile-time-only practice when defining types; basic notions are parametric in labels or namespaces for which a static representation is utterly impractical. A good example, and one that you are confronted with from the start, is that nebulous concept of sample. You will normally have raw sequences linked with samples (identified by `String`s) and you want to preserve this association throughout your types.

For example:

``` scala
case class Sample(id: String) extends Type[Any](s"$id")

case class SequenceData[P <: AnyIlluminaWhatever](
  sample: Sample
  illuminaType: P
)
extends Type[P#Raw](s"${sample.label}.${illuminaType.label}")
```

Type *values* become important too.

### data processing

If the types are parametric there's no hope of retrieving them implicitly. What we do need is functions for

1. creating an input product **type** from a value (normally a set of `String`s)
2. **easy** generate a set of labels from the output types

The key here is the connection between sets of `String`s assumed to be labels and particular types.

This should be part of the specification of the data processing bundle. As values of types are important now, we need a signature like

``` scala
// inside DataProcessingBundle
type InputContext   = ProcessingContext[Input, InputFiles]
type OutputContext  = ProcessingContext[Output, OutputFiles]

val process: InputContext => Instructions[OutputContext]
```

A cover method analogous to the current `processFiles` will take as input the set of input keys and files; from there it will parse both types and denotations and execute `process` above on them. This result is serialized to a set of labels and a map of files.
