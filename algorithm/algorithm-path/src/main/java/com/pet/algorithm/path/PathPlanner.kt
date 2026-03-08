package com.pet.algorithm.path

import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import com.pet.core.common.logger.PetLogger
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A*路径规划算法实现
 * 用于宠物在桌面上的避障移动
 */
class PathPlanner(
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val gridSize: Int = 20 // 网格大小（像素）
) {
    private var grid: Array<BooleanArray> = Array(0) { BooleanArray(0) }
    private val gridCols = screenWidth / gridSize
    private val gridRows = screenHeight / gridSize
    
    init {
        grid = Array(gridCols) { BooleanArray(gridRows) { false } }
    }
    
    /**
     * 更新障碍物地图（桌面图标位置）
     */
    fun updateObstacles(obstacles: List<Rect>) {
        // 重置网格
        for (i in grid.indices) {
            grid[i].fill(false)
        }
        
        // 将障碍物映射到网格
        obstacles.forEach { rect ->
            val startX = (rect.left / gridSize).coerceIn(0, gridCols - 1)
            val endX = (rect.right / gridSize).coerceIn(0, gridCols - 1)
            val startY = (rect.top / gridSize).coerceIn(0, gridRows - 1)
            val endY = (rect.bottom / gridSize).coerceIn(0, gridRows - 1)
            
            for (x in startX..endX) {
                for (y in startY..endY) {
                    grid[x][y] = true // 标记为障碍物
                }
            }
        }
        
        PetLogger.d("PathPlanner", "Updated obstacles: ${obstacles.size}, Grid: ${gridCols}x${gridRows}")
    }
    
    /**
     * A*算法计算最短路径
     */
    fun findPath(start: PointF, end: PointF): List<PointF> {
        val startGrid = worldToGrid(start)
        val endGrid = worldToGrid(end)
        
        if (!isValid(startGrid) || !isValid(endGrid)) {
            PetLogger.w("PathPlanner", "Invalid start or end point")
            return listOf(start, end) // 返回直线路径
        }
        
        val openList = mutableListOf<Node>()
        val closedList = mutableSetOf<GridPoint>()
        
        val startNode = Node(startGrid, 0f, heuristic(startGrid, endGrid), null)
        openList.add(startNode)
        
        while (openList.isNotEmpty()) {
            // 选择f值最小的节点
            val current = openList.minByOrNull { it.f } ?: break
            openList.remove(current)
            closedList.add(current.point)
            
            // 到达目标
            if (current.point == endGrid) {
                return reconstructPath(current).map { gridToWorld(it) }
            }
            
            // 检查邻居节点
            val neighbors = getNeighbors(current.point)
            neighbors.forEach { neighbor ->
                if (closedList.contains(neighbor) || !isValid(neighbor)) {
                    return@forEach
                }
                
                val g = current.g + 1f
                val h = heuristic(neighbor, endGrid)
                val f = g + h
                
                val existingNode = openList.find { it.point == neighbor }
                if (existingNode == null) {
                    openList.add(Node(neighbor, g, h, current))
                } else if (g < existingNode.g) {
                    existingNode.g = g
                    existingNode.f = f
                    existingNode.parent = current
                }
            }
        }
        
        // 未找到路径，返回直线
        PetLogger.w("PathPlanner", "Path not found, returning straight line")
        return listOf(start, end)
    }
    
    /**
     * 平滑路径（使用贝塞尔曲线）
     */
    fun smoothPath(roughPath: List<PointF>): List<PointF> {
        if (roughPath.size < 3) return roughPath
        
        val smoothed = mutableListOf<PointF>()
        smoothed.add(roughPath.first())
        
        for (i in 1 until roughPath.size - 1) {
            val prev = roughPath[i - 1]
            val curr = roughPath[i]
            val next = roughPath[i + 1]
            
            // 使用二次贝塞尔曲线插值
            val controlPoint = PointF(
                (prev.x + next.x) / 2f,
                (prev.y + next.y) / 2f
            )
            
            // 添加插值点
            for (t in 0.25f..0.75f step 0.25f) {
                val x = (1 - t) * (1 - t) * prev.x + 2 * (1 - t) * t * controlPoint.x + t * t * next.x
                val y = (1 - t) * (1 - t) * prev.y + 2 * (1 - t) * t * controlPoint.y + t * t * next.y
                smoothed.add(PointF(x, y))
            }
        }
        
        smoothed.add(roughPath.last())
        return smoothed
    }
    
    /**
     * 碰撞检测
     */
    fun checkCollision(position: PointF, size: Float): Boolean {
        val gridPos = worldToGrid(position)
        val radius = (size / 2 / gridSize).toInt() + 1
        
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                val x = gridPos.x + dx
                val y = gridPos.y + dy
                if (x in 0 until gridCols && y in 0 until gridRows) {
                    if (grid[x][y]) {
                        return true
                    }
                }
            }
        }
        return false
    }
    
    private fun worldToGrid(world: PointF): GridPoint {
        return GridPoint(
            (world.x / gridSize).toInt().coerceIn(0, gridCols - 1),
            (world.y / gridSize).toInt().coerceIn(0, gridRows - 1)
        )
    }
    
    private fun gridToWorld(grid: GridPoint): PointF {
        return PointF(
            grid.x * gridSize + gridSize / 2f,
            grid.y * gridSize + gridSize / 2f
        )
    }
    
    private fun isValid(point: GridPoint): Boolean {
        return point.x in 0 until gridCols &&
                point.y in 0 until gridRows &&
                !grid[point.x][point.y]
    }
    
    private fun heuristic(a: GridPoint, b: GridPoint): Float {
        // 曼哈顿距离
        return (abs(a.x - b.x) + abs(a.y - b.y)).toFloat()
    }
    
    private fun getNeighbors(point: GridPoint): List<GridPoint> {
        return listOf(
            GridPoint(point.x - 1, point.y),     // 左
            GridPoint(point.x + 1, point.y),     // 右
            GridPoint(point.x, point.y - 1),     // 上
            GridPoint(point.x, point.y + 1),     // 下
            GridPoint(point.x - 1, point.y - 1), // 左上
            GridPoint(point.x + 1, point.y - 1), // 右上
            GridPoint(point.x - 1, point.y + 1), // 左下
            GridPoint(point.x + 1, point.y + 1)  // 右下
        )
    }
    
    private fun reconstructPath(node: Node): List<GridPoint> {
        val path = mutableListOf<GridPoint>()
        var current: Node? = node
        while (current != null) {
            path.add(0, current.point)
            current = current.parent
        }
        return path
    }
    
    private data class GridPoint(val x: Int, val y: Int)
    
    private data class Node(
        val point: GridPoint,
        var g: Float,
        val h: Float,
        var parent: Node?
    ) {
        val f: Float get() = g + h
    }
}

