package org.positronicnet.orm.test

import org.positronicnet.db._
import org.positronicnet.orm._
import org.positronicnet.test.RobolectricTests

import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import com.xtremelabs.robolectric.Robolectric

object TodoDb 
  extends Database( filename = "todos.sqlite3", logTag = "todo" ) 
{
  // Note that this schema definition is for H2, the Robolectric DB engine,
  // which speaks a different dialect from SQLite...

  def schemaUpdates =
    List(""" create table todo_items (
               _id int identity,
               description varchar(100),
               is_done integer
             )
         """)
}

case class TodoItem( description: String  = null, 
                     isDone:      Boolean = false,
                     id:          Long    = ManagedRecord.unsavedId
                   )
  extends ManagedRecord
{
  def manager = TodoItem
  def isDone( newVal: Boolean ) = copy( isDone = newVal )
}

object TodoItem 
  extends RecordManager[ TodoItem ]( TodoDb("todo_items") )
{
  override def newRecord = TodoItem( null, false )
  
  mapField( "id", "_id", primaryKey = true )
  mapField( "description", "description" )
  mapField( "isDone",      "is_done" )
}

class SingleThreadOrmSpec
  extends Spec 
  with ShouldMatchers
  with BeforeAndAfterEach
  with RobolectricTests
{
  lazy val db = {
    TodoDb.openInContext( Robolectric.application )
    TodoDb
  }

  override def beforeEach = {
    db( "todo_items" ).delete
    db( "todo_items" ).insert( "description" -> "wash dog",
                                   "is_done"     -> false )
    db( "todo_items" ).insert( "description" -> "feed dog",
                                   "is_done"     -> false )
    db( "todo_items" ).insert( "description" -> "walk dog",
                                   "is_done"     -> true )
  }

  def haveItem[T<:Seq[TodoItem]]( description: String, 
                                  isDone: Boolean, 
                                  items: T ) =
    items.exists{ it => it.description == description && it.isDone == isDone }

  describe( "Single-thread ORM queries" ){
    it ("should find all the records") {
      val results = TodoItem.queryOnThisThread
      results should have size (3)

      assert( haveItem( "wash dog" , false, results ))
      assert( haveItem( "feed dog" , false, results ))
      assert( haveItem( "walk dog" , true,  results ))
    }
    it ("should retrieve only matching records with conds"){
      val undoneItems = TodoItem.whereEq( "is_done" -> false ).queryOnThisThread

      undoneItems should have size (2)
      assert( haveItem( "wash dog" , false, undoneItems ))
      assert( haveItem( "feed dog" , false, undoneItems ))
    }
  }

  describe( "Single-thread ORM count") {
    it ("should count all records with no conds") {
      TodoItem.countOnThisThread should equal (3)
    }
    it ("should count the right records with conds") {
      TodoItem.whereEq( "is_done" -> false ).countOnThisThread should equal (2)
      TodoItem.whereEq( "is_done" -> true  ).countOnThisThread should equal (1)
    }
  }

  describe( "Single-thread ORM delete via scope" ) {
    it ("should eliminate selected records") {
      TodoItem.whereEq( "is_done" -> true ).deleteAllOnThisThread
      TodoItem.countOnThisThread should equal (2)
      TodoItem.whereEq( "is_done" -> false ).countOnThisThread should equal (2)
    }
  }

  describe( "Single-thread ORM delete via record selection" ) {
    it ("should kill the selected record") {
      val doneItems = TodoItem.whereEq( "is_done" -> true ).queryOnThisThread
      doneItems should have size (1)

      doneItems.foreach{ _.delete }

      TodoItem.countOnThisThread should equal (2)
      TodoItem.whereEq( "is_done" -> false ).countOnThisThread should equal (2)
    }
  }

  describe( "Single-thread ORM update via scope" ) {
    it ("should change counts") {
      TodoItem.whereEq( "description" -> "feed dog" ).updateAllOnThisThread("is_done" -> true)
      TodoItem.whereEq( "is_done" -> true ).countOnThisThread should equal (2)
    }
  }

  describe( "Single-thread ORM update via record selection" ) {

    def doUpdate = {
      val undoneItems = TodoItem.whereEq( "is_done" -> false ).queryOnThisThread
      undoneItems should have size (2)

      val doneItem = undoneItems( 0 ).isDone( true )
      doneItem.saveOnThisThread
                      
      doneItem
    }

    it ("should change counts") {
      doUpdate
      TodoItem.countOnThisThread should equal (3)
      TodoItem.whereEq( "is_done" -> true  ).countOnThisThread should equal (2)
      TodoItem.whereEq( "is_done" -> false ).countOnThisThread should equal (1)
    }
      
    it ("should alter the record") {
      val changedItem = doUpdate
      val doneItems = TodoItem.queryOnThisThread
      assert( haveItem( changedItem.description, true, doneItems ))
    }
  }

  describe( "Single-thread ORM insert" ) {

    def doInsert = TodoItem( "train dog", false ).saveOnThisThread
    
    it ("should alter counts") {
      doInsert
      TodoItem.countOnThisThread should equal (4)
    }

    it ("should make the record show up on queries") {
      doInsert
      val items = TodoItem.queryOnThisThread
      assert( haveItem( "train dog", false, items ))
      items.find{ _.description == "train dog" }.map{ 
        _.id should not equal( -1 )
      }
    }
  }
}
