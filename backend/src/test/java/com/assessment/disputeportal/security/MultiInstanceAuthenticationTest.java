package com.assessment.disputeportal.security;
import org.junit.jupiter.api.Test;

import com.assessment.disputeportal.auth.cli.AccountAdministrationService;

import static org.assertj.core.api.Assertions.*;
class MultiInstanceAuthenticationTest extends MultiInstanceSupport {
    @Test void sharesRestartsAndLogsOutWithoutStickyRouting() throws Exception {
        create();String cookie=login(a,null);assertThat(identity(b,cookie)).isEqualTo(200);
        b.close();b=start();assertThat(identity(b,cookie)).isEqualTo(200);
        assertThat(send(a,"POST","/api/v1/auth/logout",cookie,token(b,cookie),null).statusCode()).isEqualTo(200);
        assertThat(identity(b,cookie)).isEqualTo(401);
    }
    @Test void accountMaintenanceRevokesBothSessionsAcrossBothInstances() throws Exception {
        for(String operation:new String[]{"disable-enable","role","password","delete"}) {
            reset();var account=create();String first=login(a,null),second=login(b,null);var operations=a.getBean(AccountAdministrationService.class);
            switch(operation) {
                case "disable-enable" -> { operations.setEnabled(account.id(),0,false,"isolated-test");operations.setEnabled(account.id(),1,true,"isolated-test"); }
                case "role" -> operations.setRole(account.id(),0,"AGENT","isolated-test");
                case "password" -> operations.replacePassword(account.id(),0,"different isolated password","isolated-test");
                case "delete" -> jdbc.update("DELETE FROM app_users WHERE id=?",account.id());
            }
            for(String c:new String[]{first,second}) { assertThat(identity(a,c)).isEqualTo(401);assertThat(identity(b,c)).isEqualTo(401); }
            if(operation.equals("disable-enable") || operation.equals("role"))assertThat(identity(b,login(a,null))).isEqualTo(200);
        }
    }
}
