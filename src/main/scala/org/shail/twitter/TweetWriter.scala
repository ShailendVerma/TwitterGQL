package org.shail.twitter

import com.danielasfregola.twitter4s.entities.Tweet
import spray.json.{JsBoolean, JsNumber, JsObject, JsString, JsValue, JsonWriter}

/**
  * id: ID!
  * name: String!
  * screenName: String!
  * location: String
  * description: String
  * url: String
  * followersCount: Long
  * friendsCount: Long
  * listedCount: Long
  * createdAt: String
  * favouritesCount: Long
  * utcOffset: Int
  * timeZone: String
  * geoEnabled: Boolean
  * verified: Boolean
  * statusesCount: Long
  * lang: String
  */
object TweetWriter extends JsonWriter[Tweet] {

  override def write(obj: Tweet): JsValue = JsObject(
    "user" -> JsObject(
      "id" -> JsNumber(obj.user.get.id),
      "name" -> JsString(obj.user.get.name),
      "screenName" -> JsString(obj.user.get.screen_name),
      "location" -> JsString(obj.user.get.location.get),
      "description" -> JsString(obj.user.get.description.get),
      "url" -> JsString(obj.user.get.url.get),
      "followersCount" -> JsNumber(obj.user.get.followers_count),
      "friendsCount" -> JsNumber(obj.user.get.friends_count),
      "listedCount" -> JsNumber(obj.user.get.listed_count),
      "createdAt" -> JsNumber(obj.user.get.created_at.toEpochMilli),
      "favouritesCount" -> JsNumber(obj.user.get.favourites_count),
      "utcOffset" -> JsNumber(obj.user.get.utc_offset.get),
      "timeZone" -> JsString(obj.user.get.time_zone.get),
      "geoEnabled" -> JsBoolean(obj.user.get.geo_enabled),
      "verified" -> JsBoolean(obj.user.get.verified),
      "statusesCount" -> JsNumber(obj.user.get.statuses_count),
      "lang" -> JsString(obj.user.get.lang),
    ),
    "text" -> JsString(obj.text))
}
