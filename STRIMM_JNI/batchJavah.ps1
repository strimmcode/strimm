Function Run-JavaH {
    param([string]$className)
    Write-Output "Running javah on $className"
    javah -jni -cp "./target/classes;C:/Program Files/JetBrains/IntelliJ IDEA Community Edition 2018.2.1/plugins/Kotlin/kotlinc/lib/kotlin-runtime.jar" -d ".\native\STRIMM_KotlinWrap\" $className
    Touch (".\native\STRIMM_KotlinWrap\" + $className.replace('.', '_') + ".cpp")
}

Function Touch {
    param([string]$fileName)
    Write-Output "Touching $fileName"
    Write-Output $null | Out-File -filepath $fileName -Append
}

Get-ChildItem "./target\classes\uk\co\strimm\" | 
    Where-Object { ! $_PSIsContainer } |
    ForEach-Object { "uk.co.strimm." + [io.path]::GetFileNameWithoutExtension($_) } |
    ForEach-Object { Run-JavaH($_) }
