package metadata

import com.gu.facia.client.models.{ConfigJson, FrontJson}
import controllers.FaciaControllerImpl
import org.jsoup.Jsoup
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover, FlatSpec, Matchers}
import play.api.libs.json._
import play.api.test.Helpers._
import services.{ConfigAgent, PressedPageService}
import test._
import scala.concurrent.duration._
import scala.concurrent.Await

@DoNotDiscover class FaciaMetaDataTest extends FlatSpec
  with Matchers
  with ConfiguredTestSuite
  with BeforeAndAfterAll
  with WithTestApplicationContext
  with WithMaterializer
  with WithTestWsClient
  with MockitoSugar {

  lazy val legacyPressedPageService = new PressedPageService(wsClient)
  lazy val fapi = new TestFrontJsonFapi(legacyPressedPageService)

  override def beforeAll() {
    val refresh = ConfigAgent.refreshWith(
      ConfigJson(
        fronts = Map("music" -> FrontJson(Nil, None, None, None, None, None, None, None, None, None, None, None, None, None)),
        collections = Map.empty)
    )
    Await.result(refresh, 3.seconds)
  }

  lazy val faciaController = new FaciaControllerImpl(fapi, play.api.test.Helpers.stubControllerComponents())
  val frontPath = "music"

  it should "Include organisation metadata" in {
    val result = faciaController.renderFront(frontPath)(TestRequest())
    MetaDataMatcher.ensureOrganisation(result)
  }

  it should "Include webpage metadata" in {
    val result = faciaController.renderFront(frontPath)(TestRequest(frontPath))
    MetaDataMatcher.ensureWebPage(result, frontPath)
  }

  it should "Include item list metadata" in {
    val result = faciaController.renderFront(frontPath)(TestRequest(frontPath))
    val body = Jsoup.parseBodyFragment(contentAsString(result))
    status(result) should be(200)

    val script = body.getElementsByAttributeValue("data-schema", "ItemList")
    script.size() should be(1)

    val itemList: JsValue = Json.parse(script.first().html())

    val containers = (itemList \ "itemListElement").as[JsArray].value
    containers.size should be(16)

    val topContainer = (containers(0) \ "item" \ "itemListElement").as[JsArray].value
    topContainer.size should be (45)

    (topContainer(0) \ "url").as[JsString].value should be ("/music/2017/jul/29/sza-record-company-took-my-hard-drive-beyonce-kendrick-lamar")

  }

}
