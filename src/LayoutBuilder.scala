// LayoutBuilder.scala

import scala.collection.mutable

object LayoutBuilder {

  trait Layout {
    var ready = false
    var size = 0
    val vnum = mutable.HashMap.empty[Method, Int]
    val inum = mutable.HashMap.empty[Interface, Int]
  }

  abstract class Type extends Layout {
    def interfaces: Seq[Interface]
    def methods: Seq[Method]
  }

  case class Class(superclass: Class,
                   interfaces: Seq[Interface],
                   methods: Seq[Method]) extends Type

  case class Interface(interfaces: Seq[Interface],
                       methods: Seq[Method]) extends Type

  abstract class Method

  case class PackagePrivateMethod(name: String,
                                  sig: String,
                                  packageName: String) extends Method

  case class NormalMethod(name: String,
                          sig: String) extends Method

  def traverse(interfs: TraversableOnce[Interface]): Iterator[Interface] = {
    interfs.toIterator.flatMap(i => Iterator(i) ++ traverse(i.interfaces))
  }

  def maxIMTs(t: Type) = {
    val allInterfs = traverse(t.interfaces).toSeq.distinct
    allInterfs foreach buildIMTLayout
    val nestedIMTs = allInterfs.flatMap(_.inum.keySet).toSet
    allInterfs filterNot nestedIMTs
  }

  def addIMT(pos: Int, t: Type, i: Interface): Unit = {
    t.inum(i) = pos
    t.size = pos + i.size

    for ((m, k) <- i.vnum if !t.vnum.isDefinedAt(m)) {
      t.vnum(m) = pos + k
    }

    for ((j, k) <- i.inum if !t.inum.isDefinedAt(j)) {
      t.inum(j) = pos + k
    }
  }

  def buildVMTLayout(c: Class): Unit = {
    if (c.ready) return

    val interfs = maxIMTs(c)

    // extend superclass
    if (c.superclass != null) {
      buildVMTLayout(c.superclass)
      c.size += c.superclass.size
      c.vnum ++= c.superclass.vnum
      c.inum ++= c.superclass.inum

      val lastIMTs = c.inum.keySet.filter(i => i.size > 0 &&
                                               c.inum(i) + i.size == c.size)

      def extendingOrder(x: Interface, y: Interface): Boolean = {
        if (x.size == y.size) x.inum.isDefinedAt(y)
        else x.size > y.size
      }

      var extended = false
      for {
        last <- lastIMTs.toSeq.sortWith(extendingOrder) if !extended
        extending <- interfs.find(i => !c.inum.isDefinedAt(i) &&
                                       i.inum.isDefinedAt(last) &&
                                       i.inum(last) == 0)
      } {
        addIMT(c.inum(last), c, extending)
        extended = true
      }
    }

    val inherited = c.vnum.keySet | interfs.flatMap(_.vnum.keySet).toSet
    // add new declared methods
    for (m <- c.methods if !inherited(m)) {
      c.vnum(m) = c.size
      c.size += 1
    }

    // add maximal IMTs
    for (i <- interfs.sortBy(_.size) if !c.inum.isDefinedAt(i)) {
      addIMT(c.size, c, i)
    }

    c.ready = true
  }

  def buildIMTLayout(i: Interface): Unit = {
    if (i.ready) return

    def isDisjoint(s: Interface) = (s.vnum.keySet & i.vnum.keySet).isEmpty
    // add disjoint IMTs    
    for (s <- maxIMTs(i).sortBy(-_.size) if isDisjoint(s)) {
      addIMT(i.size, i, s)
    }

    // add new declared methods
    for (m <- i.methods if !i.vnum.isDefinedAt(m)) {
      i.vnum(m) = i.size
      i.size += 1
    }

    i.ready = true
  }
}