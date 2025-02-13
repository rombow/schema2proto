/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.wire.schema

import java.io.File;
import com.squareup.wire.schema.Options.FIELD_OPTIONS
import com.squareup.wire.schema.internal.Util
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


class SchemaTest {
    @Test
    fun linkService() {
        val schema = RepoBuilder()
                .add("service.proto",
                        """
            |import "request.proto";
            |import "response.proto";
            |service Service {
            |  rpc Call (Request) returns (Response);
            |}
            """.trimMargin()
                )
                .add("request.proto",
                        """
            |message Request {
            |}
            """.trimMargin()
                )
                .add("response.proto",
                        """
            |message Response {
            |}
            """.trimMargin()
                )
                .schema()

        val service = schema.getService("Service")
        val call = service.rpc("Call")!!
        assertThat(call.requestType()).isEqualTo(schema.getType("Request").type())
        assertThat(call.responseType()).isEqualTo(schema.getType("Response").type())
    }

    @Test
    fun linkMessage() {
        val schema = RepoBuilder()
                .add("message.proto",
                        """
            |import "foo.proto";
            |message Message {
            |  optional foo_package.Foo field = 1;
            |  map<string, foo_package.Bar> bars = 2;
            |}
            """.trimMargin()
                )
                .add("foo.proto",
                        """
            |package foo_package;
            |message Foo {
            |}
            |message Bar {
            |}
            """.trimMargin()
                )
                .schema()

        val message = schema.getType("Message") as MessageType
        val field = message.field("field")
        assertThat(field!!.type()).isEqualTo(schema.getType("foo_package.Foo").type())
        val bars = message.field("bars")!!.type()
        assertThat(bars.keyType()).isEqualTo(ProtoType.STRING)
        assertThat(bars.valueType()).isEqualTo(schema.getType("foo_package.Bar").type())
    }


    @Test
    fun isValidTag() {
        assertThat(Util.isValidTag(0)).isFalse() // Less than minimum.
        assertThat(Util.isValidTag(1)).isTrue()
        assertThat(Util.isValidTag(1234)).isTrue()
        assertThat(Util.isValidTag(19222)).isFalse() // Reserved range.
        assertThat(Util.isValidTag(2319573)).isTrue()
        assertThat(Util.isValidTag(536870911)).isTrue()
        assertThat(Util.isValidTag(536870912)).isFalse() // Greater than maximum.
    }

    @Test
    fun fieldInvalidTag() {
        try {
            RepoBuilder()
                    .add("message.proto", """
            |message Message {
            |  optional int32 a = 0;
            |  optional int32 b = 1;
            |  optional int32 c = 18999;
            |  optional int32 d = 19000;
            |  optional int32 e = 19999;
            |  optional int32 f = 20000;
            |  optional int32 g = 536870911;
            |  optional int32 h = 536870912;
            |}
            """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected.message).isEqualTo(("""
            |tag is out of range: 0
            |  for field a (/source""" + File.separator + """message.proto at 2:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            |tag is out of range: 19000
            |  for field d (/source""" + File.separator + """message.proto at 5:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            |tag is out of range: 19999
            |  for field e (/source""" + File.separator + """message.proto at 6:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            |tag is out of range: 536870912
            |  for field h (/source""" + File.separator + """message.proto at 9:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin())
        }
    }

    @Test
    fun extensionsInvalidTag() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |message Message {
               |  extensions 0;
               |  extensions 1;
               |  extensions 18999;
               |  extensions 19000;
               |  extensions 19999;
               |  extensions 20000;
               |  extensions 536870911;
               |  extensions 536870912;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |tags are out of range: 0 to 0
            |  for extensions (/source""" + File.separator + """message.proto at 2:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            |tags are out of range: 19000 to 19000
            |  for extensions (/source""" + File.separator + """message.proto at 5:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            |tags are out of range: 19999 to 19999
            |  for extensions (/source""" + File.separator + """message.proto at 6:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            |tags are out of range: 536870912 to 536870912
            |  for extensions (/source""" + File.separator + """message.proto at 9:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun scalarFieldIsPacked() {
        val schema = RepoBuilder()
                .add("message.proto", """
             |message Message {
             |  repeated int32 a = 1;
             |  repeated int32 b = 2 [packed=false];
             |  repeated int32 c = 3 [packed=true];
             |}
             """.trimMargin()
                )
                .schema()

        val message = schema.getType("Message") as MessageType
        assertThat(message.field("a")!!.isPacked).isFalse()
        assertThat(message.field("b")!!.isPacked).isFalse()
        assertThat(message.field("c")!!.isPacked).isTrue()
    }

    @Test
    fun enumFieldIsPacked() {
        val schema = RepoBuilder()
                .add("message.proto", """
             |message Message {
             |  repeated HabitablePlanet home_planet = 1 [packed=true];
             |  enum HabitablePlanet {
             |    EARTH = 1;
             |  }
             |}
             """.trimMargin()
                )
                .schema()
        val message = schema.getType("Message") as MessageType
        assertThat(message.field("home_planet")!!.isPacked).isTrue()
    }

    @Test
    fun fieldIsPackedButShouldntBe() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |message Message {
               |  repeated bytes a = 1 [packed=false];
               |  repeated bytes b = 2 [packed=true];
               |  repeated string c = 3 [packed=false];
               |  repeated string d = 4 [packed=true];
               |  repeated Message e = 5 [packed=false];
               |  repeated Message f = 6 [packed=true];
               |}
               |extend Message {
               |  repeated bytes g = 7 [packed=false];
               |  repeated bytes h = 8 [packed=true];
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |packed=true not permitted on bytes
            |  for field b (/source""" + File.separator + """message.proto at 3:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            |packed=true not permitted on string
            |  for field d (/source""" + File.separator + """message.proto at 5:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            |packed=true not permitted on Message
            |  for field f (/source""" + File.separator + """message.proto at 7:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            |packed=true not permitted on bytes
            |  for field h (/source""" + File.separator + """message.proto at 11:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun fieldIsDeprecated() {
        val schema = RepoBuilder()
                .add("message.proto", """
             |message Message {
             |  optional int32 a = 1;
             |  optional int32 b = 2 [deprecated=false];
             |  optional int32 c = 3 [deprecated=true];
             |}
             """.trimMargin()
                )
                .schema()

        val message = schema.getType("Message") as MessageType
        assertThat(message.field("a")!!.isDeprecated).isFalse()
        assertThat(message.field("b")!!.isDeprecated).isFalse()
        assertThat(message.field("c")!!.isDeprecated).isTrue()
    }

    @Test
    fun fieldDefault() {
        val schema = RepoBuilder()
                .add("message.proto", """
             |message Message {
             |  optional int32 a = 1;
             |  optional int32 b = 2 [default = 5];
             |  optional bool c = 3 [default = true];
             |  optional string d = 4 [default = "foo"];
             |  optional Roshambo e = 5 [default = PAPER];
             |  enum Roshambo {
             |    ROCK = 0;
             |    SCISSORS = 1;
             |    PAPER = 2;
             |  }
             |}
             """.trimMargin()
                )
                .schema()

        val message = schema.getType("Message") as MessageType
        assertThat(message.field("a")!!.default).isNull()
        assertThat(message.field("b")!!.default).isEqualTo("5")
        assertThat(message.field("c")!!.default).isEqualTo("true")
        assertThat(message.field("d")!!.default).isEqualTo("foo")
        assertThat(message.field("e")!!.default).isEqualTo("PAPER")
    }

    @Test
    fun fieldOptions() {
        val schema = RepoBuilder()
                .add("message.proto", """
             |import "google/protobuf/descriptor.proto";
             |message Message {
             |  optional int32 a = 1;
             |  optional int32 b = 2 [color=red, deprecated=true, packed=true];
             |}
             |extend google.protobuf.FieldOptions {
             |  optional string color = 60001;
             |}
             """.trimMargin()
                )
                .schema()
        val message = schema.getType("Message") as MessageType

        val aOptions = message.field("a")!!.options()
        assertThat(aOptions.get(ProtoMember.get(FIELD_OPTIONS, "color"))).isNull()
        assertThat(aOptions.get(ProtoMember.get(FIELD_OPTIONS, "deprecated"))).isNull()
        assertThat(aOptions.get(ProtoMember.get(FIELD_OPTIONS, "packed"))).isNull()

        val bOptions = message.field("b")!!.options()
        assertThat(bOptions.get(ProtoMember.get(FIELD_OPTIONS, "color"))).isEqualTo("red")
        assertThat(bOptions.get(ProtoMember.get(FIELD_OPTIONS, "deprecated"))).isEqualTo(true)
        assertThat(bOptions.get(ProtoMember.get(FIELD_OPTIONS, "packed"))).isEqualTo(true)
    }

    @Test
    fun duplicateOption() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |import "google/protobuf/descriptor.proto";
               |message Message {
               |  optional int32 a = 1 [color=red, color=blue];
               |}
               |extend google.protobuf.FieldOptions {
               |  optional string color = 60001;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |conflicting options: red, blue
            |  for field a (/source""" + File.separator + """message.proto at 3:3)
            |  in message Message (/source""" + File.separator + """message.proto at 2:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun messageFieldTypeUnknown() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |message Message {
               |  optional foo_package.Foo unknown = 1;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |unable to resolve foo_package.Foo
            |  for field unknown (/source""" + File.separator + """message.proto at 2:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun oneofFieldTypeUnknown() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |message Message {
               |  oneof selection {
               |    int32 known = 1;
               |    foo_package.Foo unknown = 2;
               |  }
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |unable to resolve foo_package.Foo
            |  for field unknown (/source""" + File.separator + """message.proto at 4:5)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun serviceTypesMustBeNamed() {
        try {
            RepoBuilder()
                    .add("service.proto", """
               |service Service {
               |  rpc Call (string) returns (Response);
               |}
               |message Response {
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |expected a message but was string
            |  for rpc Call (/source""" + File.separator + """service.proto at 2:3)
            |  in service Service (/source""" + File.separator + """service.proto at 1:1)
            """).trimMargin()
            )
        }

        try {
            RepoBuilder()
                    .add("service.proto", """
               |service Service {
               |  rpc Call (Request) returns (string);
               |}
               |message Request {
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |expected a message but was string
            |  for rpc Call (/source""" + File.separator + """service.proto at 2:3)
            |  in service Service (/source""" + File.separator + """service.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun serviceTypesUnknown() {
        try {
            RepoBuilder()
                    .add("service.proto", """
               |service Service {
               |  rpc Call (foo_package.Foo) returns (Response);
               |}
               |message Response {
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |unable to resolve foo_package.Foo
            |  for rpc Call (/source""" + File.separator + """service.proto at 2:3)
            |  in service Service (/source""" + File.separator + """service.proto at 1:1)
            """).trimMargin()
            )
        }

        try {
            RepoBuilder()
                    .add("service.proto", """
               |service Service {
               |  rpc Call (Request) returns (foo_package.Foo);
               |}
               |message Request {
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |unable to resolve foo_package.Foo
            |  for rpc Call (/source""" + File.separator + """service.proto at 2:3)
            |  in service Service (/source""" + File.separator + """service.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun extendedTypeUnknown() {
        try {
            RepoBuilder()
                    .add("extend.proto", """
               |extend foo_package.Foo {
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |unable to resolve foo_package.Foo
            |  for extend (/source""" + File.separator + """extend.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun extendedTypeMustBeNamed() {
        try {
            RepoBuilder()
                    .add("extend.proto", """
               |extend string {
               |  optional Value value = 1000;
               |}
               |message Value {
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |expected a message but was string
            |  for extend (/source""" + File.separator + """extend.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun extendFieldTypeUnknown() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |message Message {
               |}
               |extend Message {
               |  optional foo_package.Foo unknown = 1;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |unable to resolve foo_package.Foo
            |  for field unknown (/source""" + File.separator + """message.proto at 4:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun multipleErrors() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |message Message {
               |  optional foo_package.Foo unknown = 1;
               |  optional foo_package.Foo also_unknown = 2;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |unable to resolve foo_package.Foo
            |  for field unknown (/source""" + File.separator + """message.proto at 2:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            |unable to resolve foo_package.Foo
            |  for field also_unknown (/source""" + File.separator + """message.proto at 3:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun duplicateMessageTagDisallowed() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |message Message {
               |  required string name1 = 1;
               |  required string name2 = 1;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |multiple fields share tag 1:
            |  1. name1 (/source""" + File.separator + """message.proto at 2:3)
            |  2. name2 (/source""" + File.separator + """message.proto at 3:3)
            |  for message Message (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun duplicateTagValueDisallowedInOneOf() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |message Message {
               |  required string name1 = 1;
               |  oneof selection {
               |    string name2 = 1;
               |  }
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |multiple fields share tag 1:
            |  1. name1 (/source""" + File.separator + """message.proto at 2:3)
            |  2. name2 (/source""" + File.separator + """message.proto at 4:5)
            |  for message Message (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun duplicateExtendTagDisallowed() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |message Message {
               |}
               |extend Message {
               |  optional string name1 = 1;
               |  optional string name2 = 1;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |multiple fields share tag 1:
            |  1. name1 (/source""" + File.separator + """message.proto at 4:3)
            |  2. name2 (/source""" + File.separator + """message.proto at 5:3)
            |  for message Message (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun messageNameCollisionDisallowed() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |message Message {
               |  optional string a = 1;
               |  optional string a = 2;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |multiple fields share name a:
            |  1. a (/source""" + File.separator + """message.proto at 2:3)
            |  2. a (/source""" + File.separator + """message.proto at 3:3)
            |  for message Message (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun messsageAndExtensionNameCollision() {
        val schema = RepoBuilder()
                .add("message.proto", """
             |message Message {
             |  optional string a = 1;
             |}
             """.trimMargin()
                )
                .add("extend.proto", """
             |package p;
             |import "message.proto";
             |extend Message {
             |  optional string a = 2;
             |}
             """.trimMargin()
                )
                .schema()
        val messageType = schema.getType("Message") as MessageType

        assertThat(messageType.field("a")!!.tag()).isEqualTo(1)
        assertThat(messageType.extensionField("p.a")!!.tag()).isEqualTo(2)
    }

    @Test
    fun extendNameCollisionInSamePackageDisallowed() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |message Message {
               |}
               """.trimMargin())
                    .add("extend1.proto", """
               |import "message.proto";
               |extend Message {
               |  optional string a = 1;
               |}
               """.trimMargin())
                    .add("extend2.proto", """
               |import "message.proto";
               |extend Message {
               |  optional string a = 2;
               |}
               """.trimMargin())
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |multiple fields share name a:
            |  1. a (/source""" + File.separator + """extend1.proto at 3:3)
            |  2. a (/source""" + File.separator + """extend2.proto at 3:3)
            |  for message Message (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun extendNameCollisionInDifferentPackagesAllowed() {
        val schema = RepoBuilder()
                .add("message.proto", """
             |message Message {
             |}
             """.trimMargin()
                )
                .add("extend1.proto", """
             |package p1;
             |import "message.proto";
             |extend Message {
             |  optional string a = 1;
             |}
             """.trimMargin()
                )
                .add("extend2.proto", """
             |package p2;
             |import "message.proto";
             |extend Message {
             |  optional string a = 2;
             |}
             """.trimMargin()
                )
                .schema()
        val messageType = schema.getType("Message") as MessageType

        assertThat(messageType.field("a")).isNull()
        assertThat(messageType.extensionField("p1.a")!!.packageName()).isEqualTo("p1")
        assertThat(messageType.extensionField("p2.a")!!.packageName()).isEqualTo("p2")
    }

    @Test
    fun extendEnumDisallowed() {
        try {
            RepoBuilder()
                    .add("enum.proto", """
               |enum Enum {
               |  A = 1;
               |  B = 2;
               |}
               """.trimMargin()
                    )
                    .add("extend.proto", """
               |import "enum.proto";
               |extend Enum {
               |  optional string a = 2;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |expected a message but was Enum
            |  for extend (/source""" + File.separator + """extend.proto at 2:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun requiredExtendFieldDisallowed() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |message Message {
               |}
               |extend Message {
               |  required string a = 1;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |extension fields cannot be required
            |  for field a (/source""" + File.separator + """message.proto at 4:3)
            |  in message Message (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun oneofLabelDisallowed() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |message Message {
               |  oneof string s = 1;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: IllegalStateException) {
            assertThat(expected).hasMessage("Syntax error in /source" + File.separator + "message.proto at 2:17: expected '{'")
        }
    }

    @Test
    fun duplicateEnumValueTagInScopeDisallowed() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |message Message {
               |  enum Enum1 {
               |    VALUE = 1;
               |  }
               |  enum Enum2 {
               |    VALUE = 2;
               |  }
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |multiple enums share constant VALUE:
            |  1. Message.Enum1.VALUE (/source""" + File.separator + """message.proto at 3:5)
            |  2. Message.Enum2.VALUE (/source""" + File.separator + """message.proto at 6:5)
            |  for message Message (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun duplicateEnumConstantTagWithoutAllowAliasDisallowed() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |enum Enum {
               |  A = 1;
               |  B = 1;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |multiple enum constants share tag 1:
            |  1. A (/source""" + File.separator + """message.proto at 2:3)
            |  2. B (/source""" + File.separator + """message.proto at 3:3)
            |  for enum Enum (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun duplicateEnumConstantTagWithAllowAliasFalseDisallowed() {
        try {
            RepoBuilder()
                    .add("message.proto", """
               |enum Enum {
               |  option allow_alias = false;
               |  A = 1;
               |  B = 1;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |multiple enum constants share tag 1:
            |  1. A (/source""" + File.separator + """message.proto at 3:3)
            |  2. B (/source""" + File.separator + """message.proto at 4:3)
            |  for enum Enum (/source""" + File.separator + """message.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun duplicateEnumConstantTagWithAllowAliasTrueAllowed() {
        val schema = RepoBuilder()
                .add("message.proto", """
             |enum Enum {
             |  option allow_alias = true;
             |  A = 1;
             |  B = 1;
             |}
             """.trimMargin()
                )
                .schema()
        val enumType = schema.getType("Enum") as EnumType
        assertThat(enumType.constant("A")!!.tag).isEqualTo(1)
        assertThat(enumType.constant("B")!!.tag).isEqualTo(1)
    }

    @Test
    fun fieldTypeImported() {
        val schema = RepoBuilder()
                .add("a.proto", """
             |package pa;
             |import "b.proto";
             |message A {
             |  optional pb.B b = 1;
             |}
             """.trimMargin()
                )
                .add("b.proto", """
             |package pb;
             |message B {
             |}
             """.trimMargin()
                )
                .schema()
        val a = schema.getType("pa.A") as MessageType
        val b = schema.getType("pb.B") as MessageType
        assertThat(a.field("b")!!.type()).isEqualTo(b.type())
    }

    @Test
    fun fieldMapTypeImported() {
        val schema = RepoBuilder()
                .add("a.proto", """
             |package pa;
             |import "b.proto";
             |message A {
             |  map<string, pb.B> b = 1;
             |}
             """.trimMargin()
                )
                .add("b.proto", """
             |package pb;
             |message B {
             |}
             """.trimMargin()
                )
                .schema()
        val a = schema.getType("pa.A") as MessageType
        val b = schema.getType("pb.B") as MessageType
        assertThat(a.field("b")!!.type().valueType()).isEqualTo(b.type())
    }

    @Test
    fun fieldTypeNotImported() {
        try {
            RepoBuilder()
                    .add("a.proto", """
               |package pa;
               |message A {
               |  optional pb.B b = 1;
               |}
               """.trimMargin()
                    )
                    .add("b.proto", """
               |package pb;
               |message B {
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected.message).isEqualTo(("""
            |a.proto needs to import b.proto
            |  for field b (/source""" + File.separator + """a.proto at 3:3)
            |  in message pa.A (/source""" + File.separator + """a.proto at 2:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun fieldMapTypeNotImported() {
        try {
            RepoBuilder()
                    .add("a.proto", """
               |package pa;
               |message A {
               |  map<string, pb.B> b = 1;
               |}
               """.trimMargin()
                    )
                    .add("b.proto", """
               |package pb;
               |message B {
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected.message).isEqualTo(("""
            |a.proto needs to import b.proto
            |  for field b (/source""" + File.separator + """a.proto at 3:3)
            |  in message pa.A (/source""" + File.separator + """a.proto at 2:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun rpcTypeImported() {
        val schema = RepoBuilder()
                .add("a.proto", """
             |package pa;
             |import "b.proto";
             |service Service {
             |  rpc Call (pb.B) returns (pb.B);
             |}
             """.trimMargin()
                )
                .add("b.proto", """
             |package pb;
             |message B {
             |}
             """.trimMargin()
                )
                .schema()
        val service = schema.getService("pa.Service")
        val b = schema.getType("pb.B") as MessageType
        assertThat(service.rpcs()[0].requestType()).isEqualTo(b.type())
        assertThat(service.rpcs()[0].responseType()).isEqualTo(b.type())
    }

    @Test
    fun rpcTypeNotImported() {
        try {
            RepoBuilder()
                    .add("a.proto", """
               |package pa;
               |service Service {
               |  rpc Call (pb.B) returns (pb.B);
               |}
               """.trimMargin()
                    )
                    .add("b.proto", """
               |package pb;
               |message B {
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected.message).isEqualTo(("""
            |a.proto needs to import b.proto
            |  for rpc Call (/source""" + File.separator + """a.proto at 3:3)
            |  in service pa.Service (/source""" + File.separator + """a.proto at 2:1)
            |a.proto needs to import b.proto
            |  for rpc Call (/source""" + File.separator + """a.proto at 3:3)
            |  in service pa.Service (/source""" + File.separator + """a.proto at 2:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun extendTypeImported() {
        val schema = RepoBuilder()
                .add("a.proto", """
             |package pa;
             |import "b.proto";
             |extend pb.B {
             |  optional string a = 1;
             |}
             """.trimMargin()
                )
                .add("b.proto", """
             |package pb;
             |message B {
             |  extensions 1;
             |}
             """.trimMargin()
                )
                .schema()
        val extendB = schema.protoFiles()[0].extendList()[0]
        val b = schema.getType("pb.B") as MessageType
        assertThat(extendB.type()).isEqualTo(b.type())
    }

    @Test
    fun extendTypeNotImported() {
        try {
            RepoBuilder()
                    .add("a.proto", """
               |package pa;
               |extend pb.B {
               |  optional string a = 1;
               |}
               """.trimMargin()
                    )
                    .add("b.proto", """
               |package pb;
               |message B {
               |  extensions 1;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected.message).isEqualTo(("""
            |a.proto needs to import b.proto
            |  for extend pb.B (/source""" + File.separator + """a.proto at 2:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun transitiveImportNotFollowed() {
        try {
            RepoBuilder()
                    .add("a.proto", """
               |package pa;
               |import "b.proto";
               |message A {
               |  optional pc.C c = 1;
               |}
               """.trimMargin()
                    )
                    .add("b.proto", """
               |package pb;
               |import "c.proto";
               |message B {
               |}
               """.trimMargin()
                    )
                    .add("c.proto", """
               |package pc;
               |message C {
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected.message).isEqualTo(("""
            |a.proto needs to import c.proto
            |  for field c (/source""" + File.separator + """a.proto at 4:3)
            |  in message pa.A (/source""" + File.separator + """a.proto at 3:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun transitivePublicImportFollowed() {
        val schema = RepoBuilder()
                .add("a.proto", """
             |package pa;
             |import "b.proto";
             |message A {
             |  optional pc.C c = 1;
             |}
             """.trimMargin()
                )
                .add("b.proto", """
             |package pb;
             |import public "c.proto";
             |message B {
             |}
             """.trimMargin()
                )
                .add("c.proto", """
             |package pc;
             |message C {
             |}
             """.trimMargin()
                )
                .schema()
        val a = schema.getType("pa.A") as MessageType
        val c = schema.getType("pc.C") as MessageType
        assertThat(a.field("c")!!.type()).isEqualTo(c.type())
    }

    @Test
    fun importSamePackageDifferentFile() {
        val schema = RepoBuilder()
                .add("a_b_1.proto", """
             |package a.b;
             |
             |import "a_b_2.proto";
             |
             |message MessageB {
             |  optional .a.b.MessageC c1 = 1;
             |  optional a.b.MessageC c2 = 2;
             |  optional b.MessageC c3 = 3;
             |  optional MessageC c4 = 4;
             |}
             """.trimMargin()
                )
                .add("a_b_2.proto", """
             |package a.b;
             |
             |message MessageC {
             |}
             """.trimMargin()
                )
                .schema()
        val messageC = schema.getType("a.b.MessageB") as MessageType
        assertThat(messageC.field("c1")!!.type()).isEqualTo(ProtoType.get("a.b.MessageC"))
        assertThat(messageC.field("c2")!!.type()).isEqualTo(ProtoType.get("a.b.MessageC"))
        assertThat(messageC.field("c3")!!.type()).isEqualTo(ProtoType.get("a.b.MessageC"))
        assertThat(messageC.field("c4")!!.type()).isEqualTo(ProtoType.get("a.b.MessageC"))
    }

    @Test
    fun importResolvesEnclosingPackageSuffix() {
        val schema = RepoBuilder()
                .add("a_b.proto", """
             |package a.b;
             |
             |message MessageB {
             |}
             """.trimMargin()
                )
                .add("a_b_c.proto", """
             |package a.b.c;
             |
             |import "a_b.proto";
             |
             |message MessageC {
             |  optional b.MessageB message_b = 1;
             |}
             """.trimMargin()
                )
                .schema()
        val messageC = schema.getType("a.b.c.MessageC") as MessageType
        assertThat(messageC.field("message_b")!!.type()).isEqualTo(ProtoType.get("a.b.MessageB"))
    }

    @Test
    fun importResolvesNestedPackageSuffix() {
        val schema = RepoBuilder()
                .add("a_b.proto", """
             |package a.b;
             |
             |import "a_b_c.proto";
             |
             |message MessageB {
             |  optional c.MessageC message_c = 1;
             |}
             """.trimMargin()
                )
                .add("a_b_c.proto", """
             |package a.b.c;
             |
             |message MessageC {
             |}
             """.trimMargin()
                )
                .schema()
        val messageC = schema.getType("a.b.MessageB") as MessageType
        assertThat(messageC.field("message_c")!!.type()).isEqualTo(ProtoType.get("a.b.c.MessageC"))
    }

    @Test
    fun nestedPackagePreferredOverEnclosingPackage() {
        val schema = RepoBuilder()
                .add("a.proto", """
             |package a;
             |
             |message MessageA {
             |}
             """.trimMargin()
                )
                .add("a_b.proto", """
             |package a.b;
             |
             |import "a.proto";
             |import "a_b_a.proto";
             |
             |message MessageB {
             |  optional a.MessageA message_a = 1;
             |}
             """.trimMargin()
                )
                .add("a_b_a.proto", """
             |package a.b.a;
             |
             |message MessageA {
             |}
             """.trimMargin()
                )
                .schema()
        val messageC = schema.getType("a.b.MessageB") as MessageType
        assertThat(messageC.field("message_a")!!.type()).isEqualTo(ProtoType.get("a.b.a.MessageA"))
    }

    @Test
    fun dotPrefixRefersToRootPackage() {
        val schema = RepoBuilder()
                .add("a.proto", """
             |package a;
             |
             |message MessageA {
             |}
             """.trimMargin()
                )
                .add("a_b.proto", """
             |package a.b;
             |
             |import "a.proto";
             |import "a_b_a.proto";
             |
             |message MessageB {
             |  optional .a.MessageA message_a = 1;
             |}
             """.trimMargin()
                )
                .add("a_b_a.proto", """
             |package a.b.a;
             |
             |message MessageA {
             |}
             """.trimMargin()
                )
                .schema()
        val messageC = schema.getType("a.b.MessageB") as MessageType
        assertThat(messageC.field("message_a")!!.type()).isEqualTo(ProtoType.get("a.MessageA"))
    }

    @Test
    fun dotPrefixMustBeRoot() {
        try {
            RepoBuilder()
                    .add("a_b.proto", """
               |package a.b;
               |
               |message MessageB {
               |}
               """.trimMargin()
                    )
                    .add("a_b_c.proto", """
               |package a.b.c;
               |
               |import "a_b.proto";
               |
               |message MessageC {
               |  optional .b.MessageB message_b = 1;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |unable to resolve .b.MessageB
            |  for field message_b (/source""" + File.separator + """a_b_c.proto at 6:3)
            |  in message a.b.c.MessageC (/source""" + File.separator + """a_b_c.proto at 5:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun groupsThrow() {
        try {
            RepoBuilder()
                    .add("test.proto", """
               |message SearchResponse {
               |  repeated group Result = 1 {
               |    required string url = 2;
               |    optional string title = 3;
               |    repeated string snippets = 4;
               |  }
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: IllegalStateException) {
            assertThat(expected).hasMessage("/source" + File.separator + "test.proto at 2:3: 'group' is not supported")
        }
    }

    @Test
    fun oneOfGroupsThrow() {
        try {
            RepoBuilder()
                    .add("test.proto", """
               |message Message {
               |  oneof hi {
               |    string name = 1;
               |
               |    group Stuff = 3 {
               |      optional int32 result_per_page = 4;
               |      optional int32 page_count = 5;
               |    }
               |  }
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: IllegalStateException) {
            assertThat(expected).hasMessage("/source" + File.separator + "test.proto at 5:5: 'group' is not supported")
        }
    }

    @Test
    fun reservedTagThrowsWhenUsed() {
        try {
            RepoBuilder()
                    .add("test.proto", """
               |message Message {
               |  reserved 1;
               |  optional string name = 1;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |tag 1 is reserved (/source""" + File.separator + """test.proto at 2:3)
            |  for field name (/source""" + File.separator + """test.proto at 3:3)
            |  in message Message (/source""" + File.separator + """test.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun reservedTagRangeThrowsWhenUsed() {
        try {
            RepoBuilder()
                    .add("test.proto", """
               |message Message {
               |  reserved 1 to 3;
               |  optional string name = 2;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |tag 2 is reserved (/source""" + File.separator + """test.proto at 2:3)
            |  for field name (/source""" + File.separator + """test.proto at 3:3)
            |  in message Message (/source""" + File.separator + """test.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun reservedNameThrowsWhenUsed() {
        try {
            RepoBuilder()
                    .add("test.proto", """
               |message Message {
               |  reserved 'foo';
               |  optional string foo = 1;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |name 'foo' is reserved (/source""" + File.separator + """test.proto at 2:3)
            |  for field foo (/source""" + File.separator + """test.proto at 3:3)
            |  in message Message (/source""" + File.separator + """test.proto at 1:1)
            """).trimMargin()
            )
        }
    }

    @Test
    fun reservedTagAndNameBothReported() {
        try {
            RepoBuilder()
                    .add("test.proto", """
               |message Message {
               |  reserved 'foo';
               |  reserved 1;
               |  optional string foo = 1;
               |}
               """.trimMargin()
                    )
                    .schema()
            Assertions.assertTrue(false)
        } catch (expected: SchemaException) {
            assertThat(expected).hasMessage(("""
            |name 'foo' is reserved (/source""" + File.separator + """test.proto at 2:3)
            |  for field foo (/source""" + File.separator + """test.proto at 4:3)
            |  in message Message (/source""" + File.separator + """test.proto at 1:1)
            |tag 1 is reserved (/source""" + File.separator + """test.proto at 3:3)
            |  for field foo (/source""" + File.separator + """test.proto at 4:3)
            |  in message Message (/source""" + File.separator + """test.proto at 1:1)
            """).trimMargin()
            )
        }
    }
}
