package com.beachape.config

import org.scalatest.{FunSpec, Matchers}

class AppConfSpec extends FunSpec with Matchers {

  describe("loading the config") {

    it("should work") {

      AppConf.load() shouldBe 'right

    }
  }

}
