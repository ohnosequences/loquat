package ohnosequences.nispero.bundles


import ohnosequences.nispero._
import ohnosequences.awstools.s3.{S3}
import java.io.File
import org.clapper.avsl.Logger
import ohnosequences.awstools.s3.LoadingManager
import org.apache.commons.io.{FileUtils}
import ohnosequences.nispero.utils.Utils

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._


trait AnyScriptExecutor extends ohnosequences.nispero.bundles.AnyInstructions {

  val configureScript: String

  val instructionsScript: String

  val logger = Logger(this.getClass)

  val instructions = new ohnosequences.nispero.Instructions {

    val scriptname = "script.sh"

    var loadManager: LoadingManager = null

    def execute(s3: S3, task: AnyTask, workingDir: File): TaskResult = {
      try {

        workingDir.mkdir()

        val APP_DIR = new File(workingDir, "scriptExecutor")
        logger.info("cleaning working directory: " + APP_DIR.getAbsolutePath)
        FileUtils.deleteDirectory(APP_DIR)

        logger.info("creating working directory: " + APP_DIR.getAbsolutePath)
        APP_DIR.mkdir()

        val scriptFile = new File(APP_DIR, scriptname)

        logger.info("writing script to " + scriptFile.getAbsolutePath)
        Utils.writeStringToFile(fixLineEndings(instructionsScript), scriptFile)

        //download input objects
        val inputObjects = new File(APP_DIR, "input")
        inputObjects.mkdir()
        inputObjects.listFiles().foreach(_.delete())


        val outputObjects = new File(APP_DIR, "output")
        outputObjects.mkdir()
        outputObjects.listFiles().foreach(_.delete())

        task match {
          case TinyTask(_, inputObjs, _) =>
            for ((name, content) <- inputObjs) {
              logger.info("trying to create input object: " + name)
              /* if it's a tiny task, we just create the files with input messages */
              Utils.writeStringToFile(content, new File(inputObjects, name))
              logger.info("success")
            }
          case BigTask(_, inputObjs, _) =>
            for ((name, objAddress) <- inputObjs) {
              logger.info("trying to create input object: " + name)
              if (loadManager == null) {
                logger.info("creating download manager")
                loadManager = s3.createLoadingManager()
              }
              /* if it's a big task, we download objects from S3 */
              loadManager.download(objAddress, new File(inputObjects, name))
              logger.info("success")
            }
        }

        logger.info("running instructions script in " + APP_DIR.getAbsolutePath)
        val p = sys.process.Process(Seq("bash", "-x", scriptname), APP_DIR).run()

        val result = p.exitValue()

        val messageFile = new File(APP_DIR, "message")

        val message = if (messageFile.exists()) {
          scala.io.Source.fromFile(messageFile).mkString
        } else {
          logger.warn("couldn't found message file")
          ""
        }

        if (result != 0) {
          logger.error("script finished with non zero code: " + result)
          if (message.isEmpty) {
            TaskResult.Failure("script finished with non zero code: " + result)
          } else {
            TaskResult.Failure(message)
          }
        } else {
          logger.info("start.sh script finished, uploading results")
          for ((name, objectAddress) <- task.outputObjects) {
            val outputFile = new File(outputObjects, name)
            if (outputFile.exists()) {
              logger.info("trying to publish output object " + objectAddress)
              // TODO: publicity should be a configurable option
              s3.putObject(objectAddress, outputFile, public = true)
              logger.info("success")
            } else {
              logger.warn("warning: file " + outputFile.getAbsolutePath + " doesn't exists!")
            }
          }
          TaskResult.Success(message)
        }
      } catch {
        case e: Throwable => {
          e.printStackTrace()
          TaskResult.Failure(e.getMessage)
        }
      }
    }
  }


  def fixLineEndings(s: String): String = s.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n")

  def install: Results = {
    val configureScriptName = "configure.sh"

    Utils.writeStringToFile(fixLineEndings(configureScript), new File(configureScriptName))

    logger.info("running configure script")

    sys.process.Process(Seq("bash", "-x", configureScriptName)).! match {
      case 0 => success("configure.sh finished")
      case code => failure("configure.sh fails with error code: " + code)
    }
  }
}


abstract class ScriptExecutor(deps: AnyBundle*)
  extends Bundle(deps: _*) with AnyScriptExecutor
