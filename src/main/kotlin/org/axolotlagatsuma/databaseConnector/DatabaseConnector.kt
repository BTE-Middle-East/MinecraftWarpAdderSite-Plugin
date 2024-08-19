package org.axolotlagatsuma.databaseconnector

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.sql.SQLException

class DatabaseConnector : JavaPlugin(), CommandExecutor {

    private lateinit var databaseManager: DatabaseManager

    override fun onEnable() {
        // Load initial configuration
        saveDefaultConfig()
        loadConfiguration()

        // Register the db command
        this.getCommand("db")?.setExecutor(this)

        // Schedule the task to run every minute (1200 ticks)
        val scheduler = server.scheduler
        scheduler.runTaskTimer(this, Runnable {
            databaseManager.copyFilesToDirectory()
        }, 0L, 1200L)

        logger.info("Plugin Enabled")
    }

    override fun onDisable() {
        databaseManager.close()
        logger.info("Plugin Disabled")
    }

    private fun loadConfiguration() {
        reloadConfig()
        databaseManager = DatabaseManager(
            config.getString("database.url")!!,
            config.getString("database.username")!!,
            config.getString("database.password")!!,
            config.getInt("database.poolSize"),
            config.getString("database.tableName")!!,
            config.getString("targetDirectory")!!
        )
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (command.name.equals("db", ignoreCase = true)) {
            if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
                if (sender.hasPermission("databasemanager.reload")) {
                    loadConfiguration()
                    sender.sendMessage("§aConfiguration reloaded successfully.")
                } else {
                    sender.sendMessage("§cYou do not have permission to execute this command.")
                }
                return true
            } else {
                sender.sendMessage("§cUsage: /$label reload")
                return false
            }
        }
        return false
    }

    class DatabaseManager(
        private val url: String,
        private val username: String,
        private val password: String,
        private val poolSize: Int,
        private val tableName: String,
        private val targetDirectory: String
    ) {

        private val dataSource: HikariDataSource

        init {
            val config = HikariConfig().apply {
                jdbcUrl = url
                this.username = this@DatabaseManager.username
                this.password = this@DatabaseManager.password
                maximumPoolSize = poolSize
            }

            dataSource = HikariDataSource(config)
        }

        fun copyFilesToDirectory() {
            val directory = File(targetDirectory)
            if (!directory.exists()) {
                directory.mkdirs() // Create the directory if it doesn't exist
            }

            val sql = "SELECT name, x, y, z, file_content FROM $tableName"

            try {
                dataSource.connection.use { connection ->
                    connection.prepareStatement(sql).use { statement ->
                        statement.executeQuery().use { resultSet ->
                            while (resultSet.next()) {
                                val fileName = resultSet.getString("name")
                                val x = resultSet.getFloat("x").toInt()
                                val y = resultSet.getFloat("y").toInt()
                                val z = resultSet.getFloat("z").toInt()
                                val fileContent = resultSet.getString("file_content")

                                val worldId = "1c617a1b-94f2-4311-9ae2-c4102bf1e96f" // Example UUID
                                val worldName = "world"
                                val yaw = 0
                                val pitch = 0
                                val lastOwnerId = "ea0064bf-04dc-419e-8a0d-311c9a2b7a87" // Example UUID

                                val content = """
                                    world: $worldId
                                    world-name: $worldName
                                    x: $x
                                    y: $y
                                    z: $z
                                    yaw: $yaw
                                    pitch: $pitch
                                    name: $fileName
                                    lastowner: $lastOwnerId
                                """.trimIndent()

                                val targetFile = File(directory, "$fileName.yml")

                                try {
                                    targetFile.writeText(content)
                                } catch (e: Exception) {
                                    Bukkit.getServer().logger.severe("Failed to write file: $fileName. Error: ${e.message}")
                                }
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                Bukkit.getServer().logger.severe("Failed to retrieve files from database: ${e.message}")
            }
        }

        fun close() {
            dataSource.close()
        }
    }
}
