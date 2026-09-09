package ru.neverland.townybuilds;
import java.util.OptionalInt;
import ru.neverland.townybuilds.army.MobilizationPolicy;
import static ru.neverland.townybuilds.army.MobilizationPolicy.Result.*;
public final class MobilizationSmoke {
 public static void main(String[] args) {
  check(MobilizationPolicy.check(true, 1, true, OptionalInt.of(18), false, 0, 10) == ALLOWED, "18 inclusive");
  check(MobilizationPolicy.check(true, 1, true, OptionalInt.of(17), false, 0, 10) == UNDER_AGE, "underage");
  check(MobilizationPolicy.check(true, 1, true, OptionalInt.empty(), false, 0, 10) == UNKNOWN_AGE, "unknown age");
  check(MobilizationPolicy.check(false, 5, true, OptionalInt.of(30), false, 0, 50) == FORBIDDEN, "unauthorized officer");
  check(MobilizationPolicy.check(true, 0, true, OptionalInt.of(30), false, 0, 10) == NO_BUILDING, "independent building required");
  check(MobilizationPolicy.check(true, 5, false, OptionalInt.of(30), false, 0, 50) == FOREIGN_RESIDENT, "foreign town");
  check(MobilizationPolicy.check(true, 1, true, OptionalInt.of(30), true, 1, 10) == ALREADY_MOBILIZED, "no duplicate");
  check(MobilizationPolicy.check(true, 1, true, OptionalInt.of(30), false, 10, 10) == FULL, "capacity boundary");
  for (String invalid : new String[]{"%passport_age%", "", "18.5", "-18", "999", "18 лет"}) check(MobilizationPolicy.parseAge(invalid).isEmpty(), "bad age " + invalid);
  check(MobilizationPolicy.parseAge("18").orElse(-1) == 18, "passport integer");
  System.out.println("MobilizationSmoke OK: age 18, unknown age, rights, town, building and capacity");
 }
 private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
