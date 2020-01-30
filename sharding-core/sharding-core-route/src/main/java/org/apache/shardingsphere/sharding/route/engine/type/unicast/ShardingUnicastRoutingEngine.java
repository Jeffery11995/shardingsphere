/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.sharding.route.engine.type.unicast;

import com.google.common.collect.Sets;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.sharding.route.engine.type.ShardingRouteEngine;
import org.apache.shardingsphere.underlying.common.config.exception.ShardingSphereConfigurationException;
import org.apache.shardingsphere.underlying.route.context.RouteResult;
import org.apache.shardingsphere.underlying.route.context.RouteUnit;
import org.apache.shardingsphere.underlying.route.context.TableUnit;
import org.apache.shardingsphere.core.rule.DataNode;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.core.rule.TableRule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sharding unicast routing engine.
 *
 * @author zhangliang
 * @author maxiaoguang
 */
@RequiredArgsConstructor
public final class ShardingUnicastRoutingEngine implements ShardingRouteEngine {
    
    private final Collection<String> logicTables;
    
    @Override
    public RouteResult route(final ShardingRule shardingRule) {
        RouteResult result = new RouteResult();
        if (shardingRule.isAllBroadcastTables(logicTables)) {
            List<TableUnit> tableUnits = new ArrayList<>(logicTables.size());
            for (String each : logicTables) {
                tableUnits.add(new TableUnit(each, each));
            }
            RouteUnit routeUnit = new RouteUnit(shardingRule.getShardingDataSourceNames().getRandomDataSourceName());
            routeUnit.getTableUnits().addAll(tableUnits);
            result.getRouteUnits().add(routeUnit);
        } else if (logicTables.isEmpty()) {
            result.getRouteUnits().add(new RouteUnit(shardingRule.getShardingDataSourceNames().getRandomDataSourceName()));
        } else if (1 == logicTables.size()) {
            String logicTableName = logicTables.iterator().next();
            if (!shardingRule.findTableRule(logicTableName).isPresent()) {
                result.getRouteUnits().add(new RouteUnit(shardingRule.getShardingDataSourceNames().getRandomDataSourceName()));
                return result;
            }
            DataNode dataNode = shardingRule.getDataNode(logicTableName);
            RouteUnit routeUnit = new RouteUnit(dataNode.getDataSourceName());
            routeUnit.getTableUnits().add(new TableUnit(logicTableName, dataNode.getTableName()));
            result.getRouteUnits().add(routeUnit);
        } else {
            List<TableUnit> tableUnits = new ArrayList<>(logicTables.size());
            Set<String> availableDatasourceNames = null;
            boolean first = true;
            for (String each : logicTables) {
                TableRule tableRule = shardingRule.getTableRule(each);
                DataNode dataNode = tableRule.getActualDataNodes().get(0);
                tableUnits.add(new TableUnit(each, dataNode.getTableName()));
                Set<String> currentDataSourceNames = new HashSet<>(tableRule.getActualDatasourceNames().size());
                for (DataNode eachDataNode : tableRule.getActualDataNodes()) {
                    currentDataSourceNames.add(eachDataNode.getDataSourceName());
                }
                if (first) {
                    availableDatasourceNames = currentDataSourceNames;
                    first = false;
                } else {
                    availableDatasourceNames = Sets.intersection(availableDatasourceNames, currentDataSourceNames);
                }
            }
            if (availableDatasourceNames.isEmpty()) {
                throw new ShardingSphereConfigurationException("Cannot find actual datasource intersection for logic tables: %s", logicTables);
            }
            RouteUnit routeUnit = new RouteUnit(shardingRule.getShardingDataSourceNames().getRandomDataSourceName(availableDatasourceNames));
            routeUnit.getTableUnits().addAll(tableUnits);
            result.getRouteUnits().add(routeUnit);
        }
        return result;
    }
}