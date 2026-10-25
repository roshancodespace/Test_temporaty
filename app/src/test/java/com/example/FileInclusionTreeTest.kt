package com.example

import com.example.data.model.FileNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileInclusionTreeTest {

    private fun createSampleTree(): List<FileNode> {
        val childFileA = FileNode(
            id = "src/main/A.kt",
            name = "A.kt",
            relativePath = "src/main/A.kt",
            isDirectory = false,
            isIncluded = true,
            extension = "kt"
        )
        val childFileB = FileNode(
            id = "src/main/B.kt",
            name = "B.kt",
            relativePath = "src/main/B.kt",
            isDirectory = false,
            isIncluded = true,
            extension = "kt"
        )
        val dirMain = FileNode(
            id = "src/main",
            name = "main",
            relativePath = "src/main",
            isDirectory = true,
            isIncluded = true,
            children = listOf(childFileA, childFileB)
        )
        val dirSrc = FileNode(
            id = "src",
            name = "src",
            relativePath = "src",
            isDirectory = true,
            isIncluded = true,
            children = listOf(dirMain)
        )
        val rootReadme = FileNode(
            id = "README.md",
            name = "README.md",
            relativePath = "README.md",
            isDirectory = false,
            isIncluded = true,
            extension = "md"
        )
        return listOf(dirSrc, rootReadme)
    }

    private fun collectFiles(node: FileNode, outList: MutableList<FileNode>) {
        if (!node.isDirectory) outList.add(node)
        else node.children.forEach { collectFiles(it, outList) }
    }

    private fun setAllDescendants(node: FileNode, included: Boolean): FileNode {
        return node.copy(
            isIncluded = included,
            children = node.children.map { setAllDescendants(it, included) }
        )
    }

    private fun toggleNode(nodes: List<FileNode>, targetId: String): List<FileNode> {
        fun updateTree(list: List<FileNode>): List<FileNode> {
            return list.map { node ->
                if (node.id == targetId) {
                    if (node.isDirectory) {
                        val files = mutableListOf<FileNode>()
                        collectFiles(node, files)
                        val allInc = files.isNotEmpty() && files.all { it.isIncluded }
                        setAllDescendants(node, !allInc)
                    } else {
                        node.copy(isIncluded = !node.isIncluded)
                    }
                } else if (node.isDirectory) {
                    node.copy(children = updateTree(node.children))
                } else {
                    node
                }
            }
        }

        fun recompute(node: FileNode): FileNode {
            if (!node.isDirectory) return node
            val updatedChildren = node.children.map { recompute(it) }
            val files = mutableListOf<FileNode>()
            node.copy(children = updatedChildren).let { collectFiles(it, files) }
            val allInc = files.isNotEmpty() && files.all { it.isIncluded }
            return node.copy(isIncluded = allInc, children = updatedChildren)
        }

        return updateTree(nodes).map { recompute(it) }
    }

    @Test
    fun testDisablingParentFolder_unchecksAllDescendants() {
        val initialTree = createSampleTree()
        // Disable "src" folder
        val updated = toggleNode(initialTree, "src")

        val srcNode = updated.first { it.id == "src" }
        assertFalse("Parent folder must be marked unselected", srcNode.isIncluded)

        val mainNode = srcNode.children.first { it.id == "src/main" }
        assertFalse("Subfolder must be marked unselected", mainNode.isIncluded)

        val fileA = mainNode.children.first { it.id == "src/main/A.kt" }
        val fileB = mainNode.children.first { it.id == "src/main/B.kt" }
        assertFalse("Child file A must be unselected when parent is disabled", fileA.isIncluded)
        assertFalse("Child file B must be unselected when parent is disabled", fileB.isIncluded)

        // Sibling file outside "src" remains untouched
        val readme = updated.first { it.id == "README.md" }
        assertTrue("Sibling file outside parent must remain included", readme.isIncluded)
    }

    @Test
    fun testReenablingParentFolder_checksAllDescendants() {
        val initialTree = createSampleTree()
        val disabled = toggleNode(initialTree, "src")
        val reEnabled = toggleNode(disabled, "src")

        val srcNode = reEnabled.first { it.id == "src" }
        assertTrue("Parent folder must be re-selected", srcNode.isIncluded)

        val mainNode = srcNode.children.first { it.id == "src/main" }
        assertTrue(mainNode.isIncluded)

        val fileA = mainNode.children.first { it.id == "src/main/A.kt" }
        val fileB = mainNode.children.first { it.id == "src/main/B.kt" }
        assertTrue("Child file A must be reselected", fileA.isIncluded)
        assertTrue("Child file B must be reselected", fileB.isIncluded)
    }

    @Test
    fun testTogglingSingleChild_updatesParentInclusion() {
        val initialTree = createSampleTree()
        // Uncheck single child A.kt
        val updated = toggleNode(initialTree, "src/main/A.kt")

        val srcNode = updated.first { it.id == "src" }
        val mainNode = srcNode.children.first { it.id == "src/main" }
        val fileA = mainNode.children.first { it.id == "src/main/A.kt" }
        val fileB = mainNode.children.first { it.id == "src/main/B.kt" }

        assertFalse(fileA.isIncluded)
        assertTrue(fileB.isIncluded)
        assertFalse("Parent should not be fully included if a child is unselected", mainNode.isIncluded)
        assertFalse("Root parent should not be fully included", srcNode.isIncluded)
    }
}
