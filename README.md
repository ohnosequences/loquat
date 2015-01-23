### Nisperito — nispero with tiny tasks

[**Nispero**](https://github.com/ohnosequences/nispero) is a Scala library designed for scaling stateless computations using [Amazon Web Services](http://aws.amazon.com).

**Nisperito** is a fork of Nispero the primary goal of which is to add the feature to work with small input data for workers. Basically it means that if input data is so small that it can fit in the SQS messages (<256KB) we can pass it in the tasks messages instead of creating a lot of tiny input objects in S3.

Goals of this fork:

- [x] tasks content is transferred to workers in the SQS messages (no S3 involved) (See [#1](https://github.com/laughedelic/nisperito/pull/1))
- [x] nispero-cli is removed, as it's not used
- [ ] migrate from lift-json to [upickle](https://github.com/lihaoyi/upickle) (See [#2](https://github.com/laughedelic/nisperito/pull/2))
- [ ] support both kinds of tasks: tiny (in SQS message) and big (in S3 objects)
- [ ] better console dashboard: informative charts, etc.
- [ ] upgrade to Scala-2.11 and clean-up in general
