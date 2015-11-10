package io.taig.android.parcelable

import shapeless._
import shapeless.labelled._
import shapeless.ops.hlist.{ Length, LeftFolder }
import shapeless.Nat.toInt
import shapeless.ops.nat.ToInt
import shapeless.syntax.std.tuple._
import shapeless.syntax.singleton._

/**
 * Type class that instructs how to deserialize/serialize a value from/to a Bundle
 */
object Bundleable {
    def from[T: Read: Write]: Read[T] with Write[T] = new Read[T] with Write[T] {
        override def read( bundle: Bundle ) = implicitly[Read[T]].read( bundle )

        override def write( value: T ) = implicitly[Write[T]].write( value )
    }

    trait Read[T] {
        def read( bundle: Bundle ): T
    }

    object Read {
        def apply[T]( f: Bundle ⇒ T ) = new Read[T] {
            override def read( bundle: Bundle ) = f( bundle )
        }

        implicit def `Read[Either]`[A: Bundleize.Read, B: Bundleize.Read]: Read[Either[A, B]] = Read {
            case bundle if bundle.read[Int]( "either" ) == -1 ⇒ Left( bundle.read[A]( "value" ) )
            case bundle if bundle.read[Int]( "either" ) == 1  ⇒ Right( bundle.read[B]( "value" ) )
        }

        implicit def `Read[HList]`[K <: Symbol, V, T <: HList](
            implicit
            key: Witness.Aux[K],
            bv:  Bundleize.Read[V],
            bt:  Read[T]
        ): Read[FieldType[K, V] :: T] = Read(
            bundle ⇒ field[K]( bv.read( bundle, key.value.name ) ) :: bt.read( bundle )
        )

        implicit val `Read[HNil]`: Read[HNil] = Read( _ ⇒ HNil )

        implicit def `Read[LabelledGeneric]`[T, LG](
            implicit
            lg: LabelledGeneric.Aux[T, LG],
            b:  Read[LG]
        ): Read[T] = Read( bundle ⇒ lg.from( b.read( bundle ) ) )

        implicit def `Read[Option]`[T: Bundleize.Read]: Read[Option[T]] = Read {
            case bundle: Bundle if bundle.containsKey( "option" ) ⇒ bundle.read[Int]( "option" ) match {
                case 1  ⇒ Some( bundle.read[T]( "value" ) )
                case -1 ⇒ None
            }
            case null ⇒ None
        }
    }

    trait Write[-T] {
        def write( value: T ): Bundle
    }

    object Write {
        def apply[T]( f: T ⇒ Bundle ) = new Write[T] {
            override def write( value: T ) = f( value )
        }

        implicit def `Write[Either]`[A: Bundleize.Write, B: Bundleize.Write]: Write[Either[A, B]] = Write {
            case Left( value )  ⇒ Bundle( "either" ->> -1 :: "value" ->> value :: HNil )
            case Right( value ) ⇒ Bundle( "either" ->> 1 :: "value" ->> value :: HNil )
        }

        implicit def `Write[HList]`[K <: Symbol, V, T <: HList, N <: Nat](
            implicit
            l:  Length.Aux[FieldType[K, V] :: T, N],
            ti: ToInt[N],
            lf: LeftFolder.Aux[FieldType[K, V] :: T, Bundle, fold.write.type, Bundle]
        ): Write[FieldType[K, V] :: T] = Write( _.foldLeft( new Bundle( toInt[N] ) )( fold.write ) )

        implicit val `Write[HNil]`: Write[HNil] = Write( _ ⇒ Bundle.empty )

        implicit def `Write[LabelledGeneric]`[T, LG](
            implicit
            lg: LabelledGeneric.Aux[T, LG],
            b:  Write[LG]
        ): Write[T] = Write( value ⇒ b.write( lg.to( value ) ) )

        implicit def `Write[Option]`[T: Bundleize.Write]: Write[Option[T]] = Write {
            case Some( value ) ⇒ Bundle( "option" ->> 1 :: "value" ->> value :: HNil )
            case None          ⇒ Bundle( "option" ->> -1 :: HNil )
        }

        private object fold {
            object write extends Poly2 {
                implicit def default[K <: Symbol, V: Bundleize.Write]( implicit key: Witness.Aux[K] ) = {
                    at[Bundle, FieldType[K, V]] { ( bundle, value ) ⇒
                        implicitly[Bundleize.Write[V]].write( bundle, key.value.name, value )
                        bundle
                    }
                }
            }
        }
    }
}