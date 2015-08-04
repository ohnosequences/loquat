package ohnosequences.nispero.bundles

import ohnosequences.nispero._
import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import ohnosequences.awstools.s3.S3
import java.io.File

import org.clapper.avsl.Logger
import ohnosequences.awstools.s3.LoadingManager
import org.apache.commons.io.FileUtils
import ohnosequences.nispero.utils.Utils


trait AnyInstructionsBundle extends AnyBundle {

  val logger = Logger(this.getClass)

  // def execute(s3: S3, task: AnyTask, workingDir: File = new File(".")): Results
  def execute(s3: S3, task: AnyTask, workingDir: File): Results = {
    try {
      logger.info("cleaning working directory: " + workingDir.getAbsolutePath)
      FileUtils.deleteDirectory(workingDir)
      logger.info("creating working directory: " + workingDir.getAbsolutePath)
      workingDir.mkdir()

      val inputDir = new File(workingDir, "input")
      logger.info("cleaning input directory: " + inputDir.getAbsolutePath)
      FileUtils.deleteDirectory(inputDir)
      inputDir.mkdir()

      val outputDir = new File(workingDir, "output")
      logger.info("cleaning output directory: " + outputDir.getAbsolutePath)
      FileUtils.deleteDirectory(outputDir)
      outputDir.mkdir()

      logger.info("downloading task input")
      task match {
        /* if it's a tiny task, we just create the files with input messages */
        case TinyTask(_, inputObjs, _) =>
          for ((name, content) <- inputObjs) {
            logger.info("trying to create input object: " + name)
            Utils.writeStringToFile(content, new File(inputDir, name))
            logger.info("success")
          }
        /* if it's a big task, we download objects from S3 */
        case BigTask(_, inputObjs, _) =>
          for ((name, objAddress) <- inputObjs) {
            logger.info("trying to create input object: " + name)
            s3.createLoadingManager.download(objAddress, new File(inputDir, name))
            logger.info("success")
          }
      }

      // FIXME
      logger.info("running instructions script in " + workingDir.getAbsolutePath)
      // val p = sys.process.Process(Seq("bash", "-x", scriptname), workingDir).run()
      val result = ???

      val messageFile = new File(workingDir, "message")

      val message = if (messageFile.exists()) {
        scala.io.Source.fromFile(messageFile).mkString
      } else {
        logger.warn("couldn't find message file")
        ""
      }

      if (result != 0) {
        logger.error("script finished with non zero code: " + result)
        if (message.isEmpty) {
          failure("script finished with non zero code: " + result)
        } else {
          failure(message)
        }
      } else {
        logger.info("start.sh script finished, uploading results")
        for ((name, objectAddress) <- task.outputObjects) {
          val outputFile = new File(outputDir, name)
          if (outputFile.exists()) {
            logger.info("trying to publish output object " + objectAddress)
            // TODO: publicity should be a configurable option
            s3.putObject(objectAddress, outputFile, public = true)
            logger.info("success")
          } else {
            logger.warn("warning: file " + outputFile.getAbsolutePath + " doesn't exists!")
          }
        }
        success(message)
      }
    } catch {
      case e: Throwable => {
        e.printStackTrace()
        failure(e.getMessage)
      }
    }
  }
}

abstract class InstructionsBundle(deps: AnyBundle*)
  extends Bundle(deps: _*) with AnyInstructionsBundle
