package com.leaguemate.api.controller;

import com.leaguemate.api.exception.BadRequestException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;


final class SortWhitelist {

    private SortWhitelist() {
    }

    static Pageable check(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new BadRequestException(
                        "Cannot sort by '" + order.getProperty() + "'. Allowed fields: " + allowed);
            }
        }
        return pageable;
    }
}
