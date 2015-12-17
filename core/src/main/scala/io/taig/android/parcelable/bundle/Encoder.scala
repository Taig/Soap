package io.taig.android.parcelable.bundle

import io.taig.android.parcelable
import io.taig.android.parcelable._
import io.taig.android.parcelable.functional._
import shapeless.Lazy

import scala.language.higherKinds

trait Encoder[V] extends parcelable.Encoder[( Bundle, String, V ), Unit]

object Encoder extends EncoderOperations with Encoders0

trait Encoders0 extends EncoderOperations

trait EncoderOperations {
    def apply[V: Encoder]: Encoder[V] = implicitly[Encoder[V]]

    def instance[V]( f: ( Bundle, String, V ) ⇒ Unit ): Encoder[V] = new Encoder[V] {
        override def encode( value: ( Bundle, String, V ) ) = f.tupled( value )
    }

    implicit val `Contramap[Encoder]`: Contramap[Encoder] = new Contramap[Encoder] {
        override def contramap[A, B]( b: Encoder[A] )( f: B ⇒ A ) = new Encoder[B] {
            override def encode( value: ( Bundle, String, B ) ) = b.encode( value.copy( _3 = f( value._3 ) ) )
        }
    }
}