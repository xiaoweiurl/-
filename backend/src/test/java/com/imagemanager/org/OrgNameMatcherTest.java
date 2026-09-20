package com.imagemanager.org;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrgNameMatcherTest {

    @Test
    void normalizeStripsSpacesAndFullWidthSpace() {
        assertEquals("张三", OrgNameMatcher.normalize("  张 三  "));
        assertEquals("张三", OrgNameMatcher.normalize("张\u3000三"));
        assertEquals("", OrgNameMatcher.normalize("   "));
        assertEquals("", OrgNameMatcher.normalize(null));
    }

    @Test
    void namesEqualIgnoresCaseAndInnerSpace() {
        assertTrue(OrgNameMatcher.namesEqual("Alice", "alice"));
        assertTrue(OrgNameMatcher.namesEqual("张 三", "张三"));
        assertFalse(OrgNameMatcher.namesEqual("张三", "张三丰"));
        assertFalse(OrgNameMatcher.namesEqual("", "张三"));
        assertFalse(OrgNameMatcher.namesEqual(null, "张三"));
    }

    @Test
    void matchExactReturnsAllHomonyms() {
        record Person(String name) {}
        List<Person> people = List.of(
                new Person("张三"),
                new Person("张 三"),
                new Person("李四"),
                new Person("张三丰"));
        List<Person> matched = OrgNameMatcher.matchExact(
                "张三", people, person -> person == null ? null : person.name());
        assertEquals(2, matched.size());
    }

    @Test
    void requireNameRejectsBlank() {
        assertEquals("张三", OrgNameMatcher.requireName(" 张三 "));
        assertThrows(IllegalArgumentException.class, () -> OrgNameMatcher.requireName("  "));
        assertThrows(IllegalArgumentException.class, () -> OrgNameMatcher.requireName(null));
    }

    @Test
    void matchesUserDisplayNameOnUsernameOrNickname() {
        assertTrue(OrgNameMatcher.matchesUserDisplayName("洪庭杰", "洪庭杰", null));
        assertTrue(OrgNameMatcher.matchesUserDisplayName("洪庭杰", "admin", "洪 庭杰"));
        assertFalse(OrgNameMatcher.matchesUserDisplayName("洪庭杰", "0924464954", "dt-user"));
    }

    @Test
    void lookupKeysIncludesTrimmedAndNormalized() {
        assertEquals(List.of("张 三", "张三"), OrgNameMatcher.lookupKeys(" 张 三 "));
        assertEquals(List.of("洪庭杰"), OrgNameMatcher.lookupKeys("洪庭杰"));
        assertEquals(List.of(), OrgNameMatcher.lookupKeys("  "));
    }
}
