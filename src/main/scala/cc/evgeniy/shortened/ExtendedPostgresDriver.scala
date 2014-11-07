package cc.evgeniy.shortened

import slick.driver.PostgresDriver
import com.github.tminglei.slickpg._

trait ExtendedPostgresDriver extends PostgresDriver
  with PgNetSupport
//  with PgArraySupport
//  with PgDateSupport
//  with PgRangeSupport
//  with PgHStoreSupport
//  with PgSearchSupport
//  with PgPlayJsonSuprport
//with PgPostGISSupport
{

  override lazy val Implicit = new ImplicitsPlus {}
    override val simple = new SimpleQLPlus {}

  //////
  trait ImplicitsPlus extends Implicits
  with NetImplicits
//    with ArrayImplicits
//    with DateTimeImplicits
//    with RangeImplicits
//    with HStoreImplicits
//    with SearchImplicits
//    with JsonImplicits
//    with PostGISImplicits

    trait SimpleQLPlus extends SimpleQL
    with ImplicitsPlus
  //  with SearchAssistants
  //  with PostGISAssistants
}

object ExtendedPostgresDriver extends ExtendedPostgresDriver