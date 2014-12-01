### Nisperito — nispero with tiny tasks

[**Nispero**](https://github.com/ohnosequences/nispero) is a Scala library designed for scaling stateless computations using [Amazon Web Services](http://aws.amazon.com).

**Nisperito** is a fork of Nispero in which I want to add a feature to work with small input data for workers. Basically it means that if input data is so small, it can fit in the SQS messages (<256KB) we can pass it in the tasks messages instead of creating thousands of tiny input objects in S3.

### Short-term plan

- Add tiny tasks feature ASAP with minimal changes

### Long-term plan

- Add it as an option (both kind of tasks at the same time)
- Send a pull-request to the original repo
- Update it for Scala-2.11 and clean-up in general
