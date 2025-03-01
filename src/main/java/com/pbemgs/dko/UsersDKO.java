package com.pbemgs.dko;

import com.pbemgs.generated.enums.UsersStatus;
import com.pbemgs.generated.enums.UsersUserType;
import com.pbemgs.generated.tables.records.UsersRecord;
import org.jooq.DSLContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.pbemgs.generated.Pbemgs.PBEMGS;

/**
 * Database interface class for the USERS table.
 * Fetches/returns the jooq record structure.
 */
public class UsersDKO {

    private final DSLContext dslContext;

    public UsersDKO(DSLContext jooqContext) {
        dslContext = jooqContext;
    }

    public UsersRecord fetchUserForEmail(String email) {
        return dslContext
                .selectFrom(PBEMGS.USERS)
                .where(PBEMGS.USERS.EMAIL_ADDR.equalIgnoreCase(email))
                .fetchOne();
    }


    public UsersRecord fetchUserForHandle(String handle) {
        return dslContext
                .selectFrom(PBEMGS.USERS)
                .where(PBEMGS.USERS.HANDLE.equalIgnoreCase(handle))
                .fetchOne();
    }

    public UsersRecord fetchUserById(Long userId) {
        if (userId == null) {
            return null;
        }

        return dslContext
                .selectFrom(PBEMGS.USERS)
                .where(PBEMGS.USERS.USER_ID.eq(userId))
                .fetchOne();
    }

    public UsersRecord fetchOwnerUser() {
        return dslContext
                .selectFrom(PBEMGS.USERS)
                .where(PBEMGS.USERS.USER_TYPE.eq(UsersUserType.OWNER))
                .fetchOne();
    }

    public Map<Long, UsersRecord> fetchUsersByIds(Set<Long> creatorUserIds) {
        if (creatorUserIds == null || creatorUserIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return dslContext
                .selectFrom(PBEMGS.USERS)
                .where(PBEMGS.USERS.USER_ID.in(creatorUserIds))
                .fetchMap(PBEMGS.USERS.USER_ID);
    }

    public List<UsersRecord> fetchAllActiveUsers() {
        return dslContext
                .selectFrom(PBEMGS.USERS)
                .where(PBEMGS.USERS.STATUS.eq(UsersStatus.ACTIVE))
                .fetch();
    }

    public Long createUser(UsersRecord newUser) {
        return dslContext.insertInto(PBEMGS.USERS)
                .set(PBEMGS.USERS.EMAIL_ADDR, newUser.getEmailAddr())
                .set(PBEMGS.USERS.HANDLE, newUser.getHandle())
                .set(PBEMGS.USERS.USER_TYPE, newUser.getUserType())
                .set(PBEMGS.USERS.STATUS, newUser.getStatus())
                .set(PBEMGS.USERS.MVP_TIER, newUser.getMvpTier())
                .returning(PBEMGS.USERS.USER_ID)  // Return the generated user ID
                .fetchOne()
                .getUserId();
    }

}
