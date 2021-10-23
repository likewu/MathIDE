/*
 * Copyright 2021 Squircle IDE contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blacksquircle.ui.feature.editor.viewmodel

import android.util.Log
import androidx.core.text.PrecomputedTextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacksquircle.ui.data.storage.keyvalue.SettingsManager
import com.blacksquircle.ui.data.utils.InternalTheme
import com.blacksquircle.ui.domain.model.documents.DocumentParams
import com.blacksquircle.ui.domain.model.editor.DocumentContent
import com.blacksquircle.ui.domain.model.editor.DocumentModel
import com.blacksquircle.ui.domain.repository.documents.DocumentRepository
import com.blacksquircle.ui.domain.repository.themes.ThemesRepository
import com.blacksquircle.ui.feature.editor.R
import com.blacksquircle.ui.feature.editor.utils.EventsQueue
import com.blacksquircle.ui.feature.editor.utils.SettingsEvent
import com.blacksquircle.ui.filesystem.base.exception.FileNotFoundException
import com.blacksquircle.ui.filesystem.base.model.FileModel
import com.blacksquircle.ui.language.base.Language
import com.blacksquircle.ui.language.base.model.ParseResult
import com.blacksquircle.ui.utils.event.SingleLiveEvent
import com.blacksquircle.ui.utils.extensions.launchEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val documentRepository: DocumentRepository,
    private val themesRepository: ThemesRepository
) : ViewModel() {

    companion object {
        private const val TAG = "EditorViewModel"
    }

    // region EVENTS

    val loadingBar = MutableLiveData(false) // Индикатор загрузки документа
    val emptyView = MutableLiveData(true) // Сообщение об отсутствии документов

    val loadFilesEvent = MutableLiveData<List<DocumentModel>>() // Загрузка недавних файлов
    val settingsEvent = EventsQueue<SettingsEvent<*>>() // События с измененными настройками

    val toastEvent = SingleLiveEvent<Int>() // Отображение сообщений
    val parseEvent = SingleLiveEvent<ParseResult>() // Проверка ошибок
    val runEvent = SingleLiveEvent<ParseResult>()
    val contentEvent = SingleLiveEvent<Pair<DocumentContent, PrecomputedTextCompat>>() // Контент загруженного файла

    val openFileEvent = SingleLiveEvent<DocumentModel>() // Открытие файла из проводника в редакторе
    val openPropertiesEvent = SingleLiveEvent<FileModel>() // Просмотр свойств выбранного файла

    // endregion EVENTS

    val autoSaveFiles: Boolean
        get() = settingsManager.autoSaveFiles

    fun loadFiles() {
        viewModelScope.launchEvent(loadingBar) {
            try {
                val documents = documentRepository.fetchDocuments()
                loadFilesEvent.value = documents
            } catch (e: Exception) {
                Log.e(TAG, e.message, e)
                toastEvent.value = R.string.message_unknown_exception
            }
        }
    }

    fun loadFile(documentModel: DocumentModel, params: PrecomputedTextCompat.Params) {
        viewModelScope.launchEvent(loadingBar) {
            try {
                val content = documentRepository.loadFile(documentModel)
                val precomputedText = withContext(Dispatchers.Default) {
                    PrecomputedTextCompat.create(content.text, params)
                }
                settingsManager.selectedDocumentId = documentModel.uuid
                contentEvent.value = content to precomputedText
            } catch (e: Throwable) {
                Log.e(TAG, e.message, e)
                when (e) {
                    is FileNotFoundException -> {
                        toastEvent.value = R.string.message_file_not_found
                    }
                    is OutOfMemoryError -> {
                        toastEvent.value = R.string.message_out_of_memory
                    }
                    else -> {
                        toastEvent.value = R.string.message_unknown_exception
                    }
                }
            }
        }
    }

    fun saveFile(documentContent: DocumentContent, params: DocumentParams) {
        viewModelScope.launch {
            try {
                documentRepository.saveFile(documentContent, params)
                if (params.local) {
                    toastEvent.value = R.string.message_saved
                }
            } catch (e: Exception) {
                Log.e(TAG, e.message, e)
                toastEvent.value = R.string.message_unknown_exception
            }
        }
    }

    fun updateDocument(documentModel: DocumentModel) {
        viewModelScope.launch {
            try {
                documentRepository.updateDocument(documentModel)
            } catch (e: Exception) {
                Log.e(TAG, e.message, e)
                toastEvent.value = R.string.message_unknown_exception
            }
        }
    }

    fun deleteDocument(documentModel: DocumentModel) {
        viewModelScope.launch {
            try {
                documentRepository.deleteDocument(documentModel)
            } catch (e: Exception) {
                Log.e(TAG, e.message, e)
                toastEvent.value = R.string.message_unknown_exception
            }
        }
    }

    fun openFile(documents: List<DocumentModel>) {
        settingsManager.selectedDocumentId = documents.last().uuid
        loadFilesEvent.value = documents
    }

    fun parse(documentModel: DocumentModel, language: Language?, sourceCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val parser = language?.getParser()
            val parseResult = parser?.execute(documentModel.name, sourceCode)
            parseResult?.let(parseEvent::postValue)
        }
    }

    fun run(documentModel: DocumentModel, language: Language?, sourceCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val parser = language?.getParser()
            val parseResult = parser?.execute(documentModel.name, sourceCode, documentModel.path)
            parseResult?.let(runEvent::postValue)
        }
    }

    fun findRecentTab(list: List<DocumentModel>): Int {
        return if (list.isNotEmpty()) {
            var position = 0
            list.forEachIndexed { index, documentModel ->
                if (documentModel.uuid == settingsManager.selectedDocumentId) {
                    position = index
                }
            }
            position
        } else {
            -1
        }
    }

    // region PREFERENCES

    fun fetchSettings() {
        viewModelScope.launch {
            val value = settingsManager.colorScheme
            val theme = InternalTheme.getTheme(value) ?: themesRepository.fetchTheme(value)
            settingsEvent.offer(SettingsEvent.ThemePref(theme))
        }

        val fontSize = settingsManager.fontSize.toFloat()
        settingsEvent.offer(SettingsEvent.FontSize(fontSize))

        val fontType = settingsManager.fontType
        settingsEvent.offer(SettingsEvent.FontType(fontType))

        val wordWrap = settingsManager.wordWrap
        settingsEvent.offer(SettingsEvent.WordWrap(wordWrap))

        val codeCompletion = settingsManager.codeCompletion
        settingsEvent.offer(SettingsEvent.CodeCompletion(codeCompletion))

        val errorHighlighting = settingsManager.errorHighlighting
        settingsEvent.offer(SettingsEvent.ErrorHighlight(errorHighlighting))

        val pinchZoom = settingsManager.pinchZoom
        settingsEvent.offer(SettingsEvent.PinchZoom(pinchZoom))

        val highlightCurrentLine = settingsManager.highlightCurrentLine
        settingsEvent.offer(SettingsEvent.CurrentLine(highlightCurrentLine))

        val highlightMatchingDelimiters = settingsManager.highlightMatchingDelimiters
        settingsEvent.offer(SettingsEvent.Delimiters(highlightMatchingDelimiters))

        val extendedKeyboard = settingsManager.extendedKeyboard
        settingsEvent.offer(SettingsEvent.ExtendedKeys(extendedKeyboard))

        val keyboardPreset = settingsManager.keyboardPreset.toCharArray().map(Char::toString)
        settingsEvent.offer(SettingsEvent.KeyboardPreset(keyboardPreset))

        val softKeyboard = settingsManager.softKeyboard
        settingsEvent.offer(SettingsEvent.SoftKeys(softKeyboard))

        val autoIndentation = settingsManager.autoIndentation
        settingsEvent.offer(SettingsEvent.AutoIndent(autoIndentation))

        val autoCloseBrackets = settingsManager.autoCloseBrackets
        settingsEvent.offer(SettingsEvent.AutoBrackets(autoCloseBrackets))

        val autoCloseQuotes = settingsManager.autoCloseQuotes
        settingsEvent.offer(SettingsEvent.AutoQuotes(autoCloseQuotes))

        val useSpacesInsteadOfTabs = settingsManager.useSpacesInsteadOfTabs
        settingsEvent.offer(SettingsEvent.UseSpacesNotTabs(useSpacesInsteadOfTabs))

        val tabWidth = settingsManager.tabWidth
        settingsEvent.offer(SettingsEvent.TabWidth(tabWidth))
    }

    // endregion PREFERENCES
}