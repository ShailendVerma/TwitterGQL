package org.shail.twitter

import akka.event.{ActorEventBus, LookupClassification}

class TwitterEventsBus extends ActorEventBus with LookupClassification {

  type Event = TweetWrapper
  type Classifier = String

  override protected def mapSize(): Int = 500

  override protected def compareSubscribers(a: Subscriber, b: Subscriber): Int = a.compareTo(b)

  override protected def classify(event: TweetWrapper): Classifier = event.tag

  override protected def publish(event: TweetWrapper, subscriber: Subscriber): Unit = subscriber ! event
}

object TwitterEventsBus {
  def apply: TwitterEventsBus = new TwitterEventsBus()
}
