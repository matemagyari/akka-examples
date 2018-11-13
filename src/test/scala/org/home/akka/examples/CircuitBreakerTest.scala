package org.home.akka.examples

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class CircuitBreakerTest extends FlatSpec with Matchers with ScalaFutures with StrictLogging {

  implicit val system: ActorSystem = ActorSystem("CircuitBreakerTest")
  import system.dispatcher

  sealed trait Response
  object Response {
    case object Success extends Response
    case object SomeError extends Response
  }

  //which responses should be interpreted as failures for the circuit breaker
  private val responseAsFailure: Try[Int] ⇒ Boolean = {
    case Success(n) ⇒ n == 500
    case Failure(_) ⇒ true
  }

  //wrap the : => Future[T] body in a circuit breaker
  private def wrap[T](
      breaker: CircuitBreaker,
      responseAsFailure: Try[T] ⇒ Boolean,
      fallbackResponse: T)(f: ⇒ Future[T]): Future[T] =
    breaker.withCircuitBreaker(f, responseAsFailure).recover {
      case _: akka.pattern.CircuitBreakerOpenException ⇒ fallbackResponse
    }

  "Circuit breaker" should "open if max failure threshold is reached" in {

    val circuitBreaker: CircuitBreaker = newCircuitBreaker(maxFailures = 5)

    val fallbackResponse = 9

    val client = new HttpClient

    def wrappedCall(i: Int): Future[Int] =
      wrap(circuitBreaker, responseAsFailure, fallbackResponse = fallbackResponse) {
        client.call(i)
      }

    client.call(0).futureValue shouldBe 200
    client.call(1).futureValue shouldBe 500
    client.call(2).recover { case _ ⇒ 3 }.futureValue shouldBe 3

    client.callsExecuted() shouldBe 3

    //let's fail 2 times

    wrappedCall(0).futureValue shouldBe 200
    wrappedCall(1).futureValue shouldBe 500
    wrappedCall(2).recover { case _ ⇒ 3 }.futureValue shouldBe 3

    client.callsExecuted() shouldBe 6

    //twice failed already, let's fail 3 more times to reach threshold
    (1 to 3).foreach { _ ⇒
      circuitBreaker.isClosed shouldBe true
      wrappedCall(1).futureValue shouldBe 500
    }

    //CircuitBreaker is open now
    circuitBreaker.isClosed shouldBe false
    client.callsExecuted() shouldBe 9

    //subsequent calls should not be executed, circuit breaker should respond with the fallback response
    (1 to 3).foreach { _ ⇒
      circuitBreaker.isClosed shouldBe false
      wrappedCall(0).futureValue shouldBe fallbackResponse
      client.callsExecuted() shouldBe 9
    }
  }

  private def newCircuitBreaker(name: String = "defaultCB", maxFailures: Int) =
    new CircuitBreaker(
      system.scheduler,
      maxFailures = maxFailures,
      callTimeout = 5 millis,
      resetTimeout = 1000 millis)
      .onOpen {
        logger.info(s"Circuit breaker [$name] is open")
      }
      .onHalfOpen {
        logger.info(s"Circuit breaker [$name] is half open")
      }
      .onClose {
        logger.info(s"Circuit breaker [$name] closed")
      }

  private class HttpClient {

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

    def callsExecuted() = counter.get()
  }

}
