
package com.example.banking.account.dto;

import com.example.banking.account.entity.AccountStatus;

public record ChangeAccountStatusRequest(
        AccountStatus status
) {
}