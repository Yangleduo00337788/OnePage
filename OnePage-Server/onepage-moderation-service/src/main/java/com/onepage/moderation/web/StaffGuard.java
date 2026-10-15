package com.onepage.moderation.web;

import com.onepage.common.ops.OpsStaffAllowlist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 运营员工白名单，来自 {@code OPS_STAFF_USER_IDS}。空名单拒绝所有人。 */
@Component
public class StaffGuard {
  private final Set<Long> staffIds;

  public StaffGuard(@Value("${onepage.ops-staff-user-ids:}") String raw) {
    this.staffIds = OpsStaffAllowlist.parse(raw);
  }

  public void requireStaff(long userId) {
    OpsStaffAllowlist.requireStaff(staffIds, userId);
  }

  public boolean isStaff(long userId) {
    return staffIds.contains(userId);
  }
}
