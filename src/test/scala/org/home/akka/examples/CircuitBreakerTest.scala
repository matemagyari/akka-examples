package org.home.akka.examples

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class CircuitBreakerTest extends FlatSpec with Matchers with ScalaFutures with StrictLogging {

  implicit val system: ActorSystem = ActorSystem("CircuitBreakerTest")
  import system.dispatcher

  val responseAsFailure: Try[Int] ⇒ Boolean = {
    case Success(n) ⇒ n == 500
    case Failure(_) ⇒ true
  }

  def wrap[T](
      breaker: CircuitBreaker,
      f: ⇒ Future[T],
      responseAsFailure: Try[T] ⇒ Boolean): Future[T] =
    breaker.withCircuitBreaker(f, responseAsFailure)

  "Circuitbreaker" should "open if max failure threshold is reached" in {

    val circuitBreaker: CircuitBreaker = newCircuitBreaker(maxFailures = 5)

    val httpCaller = new HttpCaller

    def wrappedRemoteCall(i: Int): Future[Int] =
      wrap(circuitBreaker, httpCaller.call(i), responseAsFailure)

    httpCaller.call(0).futureValue shouldBe 200
    httpCaller.call(1).futureValue shouldBe 500
    httpCaller.call(2).recover { case _ ⇒ 3 }.futureValue shouldBe 3

    httpCaller.count() shouldBe 3

    //let's fail 2 times

    wrappedRemoteCall(0).futureValue shouldBe 200
    wrappedRemoteCall(1).futureValue shouldBe 500
    wrappedRemoteCall(2).recover { case _ ⇒ 3 }.futureValue shouldBe 3

    httpCaller.count() shouldBe 6

    //twice failed already, let's fail 3 more times to reach threshold
    (1 to 3).foreach { _ ⇒
      circuitBreaker.isClosed shouldBe true
      wrappedRemoteCall(1).futureValue shouldBe 500
    }

    //CircuitBreaker is open now
    circuitBreaker.isClosed shouldBe false
    httpCaller.count() shouldBe 9

    //subsequent calls should not be executed
    (1 to 3).foreach { _ ⇒
      try {
        wrappedRemoteCall(0).futureValue
        fail("Should have thrown exception")
      } catch {
        case ex: TestFailedException
            if ex.getCause().isInstanceOf[akka.pattern.CircuitBreakerOpenException] ⇒ //ok
        case ex ⇒ fail(ex)
      }

      httpCaller.count() shouldBe 9
    }
  }

  private def newCircuitBreaker(maxFailures: Int) =
    new CircuitBreaker(
      system.scheduler,
      maxFailures = maxFailures,
      callTimeout = 5 millis,
      resetTimeout = 10 millis)

  private class HttpCaller {

    private val counter = new AtomicInteger()

    def call(input: Int, delay: FiniteDuration = 0 millis): Future[Int] = {
      counter.incrementAndGet()
      Thread.sleep(delay.toMillis)
      (input % 3) match {
        case 0 ⇒ Future.successful(200) //success
        case 1 ⇒ Future.successful(500) //failure
        case 2 ⇒ Future.failed(new RuntimeException(s"failed $input"))
      }
    }

    def count() = counter.get()
  }

}
