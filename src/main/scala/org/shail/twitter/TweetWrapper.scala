package org.shail.twitter

import com.danielasfregola.twitter4s.entities.Tweet

case class TweetWrapper(tag: String, tweet: Tweet)

object TweetWrapper {

  def generateTags(tweet: Tweet): List[TweetWrapper] =
    Topics.getMatchedTopics(tweet.text)
      .map {
        TweetWrapper(_, tweet)
      }

}
