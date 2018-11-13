package org.home.akka.examples

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Future

class DispatcherTest extends FlatSpec with Matchers with ScalaFutures with StrictLogging {

  val system = ActorSystem("DispatcherTest", ConfigFactory.load())

  ignore should "something" in {
//  "something" should "something" in {

    val dispatcher: MessageDispatcher = system.dispatchers.lookup("custom-dispatcher-1")

    //parallelism-factor=8, we have 4 processors = it can handle max 32
    //For 33 it hangs, as the start latch will never get to 0
    val numOfTasks = 32
    val endLatch = new CountDownLatch(numOfTasks)
    def createTask(id: Int): Task =
      new Task(
        id = id,
        startLatch = new CountDownLatch(numOfTasks),
        endLatch = endLatch,
        dispatcher = dispatcher)

    val start = time()

    (1 to numOfTasks).foreach { id ⇒
      createTask(id).run()
    }

    endLatch.await()

    logger.info(s"Time elapsed: ${time() - start} ms")
    system.terminate()

  }

  private def time() = System.currentTimeMillis()

  private class Task(
      id: Int,
      startLatch: CountDownLatch,
      endLatch: CountDownLatch,
      dispatcher: MessageDispatcher) {

    private val state = new AtomicInteger()

    def run(): Unit = {

      def log(action: String) = {
        logger.info(s"$id $action [${Thread.currentThread().getId}]")
      }
      Future {
        startLatch.countDown()
        startLatch.await()
        state.set(1) //started
        log("starts")
        Thread.sleep(100)
        log("ends")
        state.set(2) //ended
        endLatch.countDown()
      }(dispatcher)

      def status: Int = state.get()
    }
  }

//  private def withDispatcher(parallelismMin: Int,parallelismMax: Int, parallelismFactor: Int):
}

//parallelism-min = 2
//parallelism-max = 64
//parallelism-factor = 8
