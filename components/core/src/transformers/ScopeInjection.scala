package ch.epfl.yinyang
package transformers

import ch.epfl.yinyang._
import ch.epfl.yinyang.transformers._
import scala.reflect.macros.blackbox.Context
import language.experimental.macros

trait ScopeInjection extends MacroModule with TransformationUtils {
  import c.universe._
  import internal.decorators._

  val rewireThis: Boolean = true

  // TODO DRY
  def rewiredToThis(s: String) = s == "package" || s == "Predef"
  object ScopeInjectionTransformer extends (Tree => Tree) {
    def apply(tree: Tree) = {
      val t = new ScopeInjectionTransformer().transform(tree)
      log("scopeInjected: " + t, 2)
      t
    }
  }

  class ScopeInjectionTransformer extends Transformer {

    var ident = 0

    def preservePosition(newTree: Tree, oldTree: Tree): Tree = {
      newTree.setPos(oldTree.pos)
      newTree
    }

    override def transform(tree: Tree): Tree = {
      log(" " * ident + " --> " + tree, 3)
      ident += 1

      val result = tree match {
        //provide Def trees with NoSymbol (for correct show(tree))
        case vdDef: ValOrDefDef if rewireThis => {
          val retDef = super.transform(tree)
          retDef.setSymbol(NoSymbol)
          retDef
        }

        case CaseDef(pat: Tree, guard: Tree, body: Tree) => {
          val newPat = pat match {
            case Apply(fun: TypeTree, args) => transform(preservePosition(Apply(fun.original, args), pat))
            case _                          => transform(pat)
          }
          CaseDef(newPat, transform(guard), transform(body))
        }

        // re-wire objects
        case s @ Select(Select(inn, t: TermName), name) // package object goes to this
        if s.symbol.isMethod && (rewiredToThis(t.toString) || t.toString == "this") =>
          Ident(name)

        case s @ Select(inn, name) if s.symbol.isMethod =>
          Select(transform(inn), name)

        // replaces objects with their cake counterparts
        case s @ Select(inn, name) if s.symbol.isModule =>
          Ident(name)

        // Added to rewire inherited methods to this class
        case th @ This(_) if rewireThis =>
          This(typeNames.EMPTY)

        // Removes all import statements (for now).
        // case Import(_, _) =>
        //   EmptyTree

        case _ =>
          super.transform(tree)
      }

      ident -= 1
      log(" " * ident + " <-- " + result, 3)

      preservePosition(result, tree)
    }
  }

}