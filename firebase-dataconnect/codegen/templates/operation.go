package templates

import (
	"bytes"
	_ "embed"
	"errors"
	"fmt"
	"github.com/vektah/gqlparser/v2/ast"
	"log"
	"os"
	"path"
	"text/template"
)

//go:embed operation.gotmpl
var operationTemplate string

func LoadOperationTemplate() (*template.Template, error) {
	templateName := "operation.gotmpl"
	log.Println("Loading Go template:", templateName)

	funcMap := template.FuncMap{
		"fail":                      fail,
		"kotlinTypeFromGraphQLType": kotlinTypeFromGraphQLType,
		"isScalarType":              isScalarType,
		"flattenedVariablesFor":     flattenedVariablesFor,
		"createConvenienceFunctionVariablesArgumentsRecursiveArgFromConfig":     createConvenienceFunctionVariablesArgumentsRecursiveArgFromConfig,
		"createConvenienceFunctionVariablesArgumentsRecursiveArgFromArgAndType": createConvenienceFunctionVariablesArgumentsRecursiveArgFromArgAndType,
		"pickedFieldsForVariableDefinition":                                     pickedFieldsForVariableDefinition,
	}

	return template.New(templateName).Funcs(funcMap).Parse(operationTemplate)
}

type RenderOperationTemplateConfig struct {
	KotlinPackage string
	Operation     *ast.OperationDefinition
	Schema        *ast.Schema
}

func RenderOperationTemplate(
	tmpl *template.Template,
	outputFile string,
	config RenderOperationTemplateConfig) error {

	log.Println("Generating:", outputFile)

	var outputBuffer bytes.Buffer
	err := tmpl.Execute(&outputBuffer, config)
	if err != nil {
		return err
	}

	outputDir := path.Dir(outputFile)
	_, err = os.Stat(outputDir)
	if os.IsNotExist(err) {
		err = os.MkdirAll(outputDir, 0755)
		if err != nil {
			return err
		}
	}

	err = os.WriteFile(outputFile, outputBuffer.Bytes(), 0644)
	if err != nil {
		return err
	}

	return nil
}

func kotlinTypeFromGraphQLType(node *ast.Type) string {
	var suffix string
	if node.NonNull {
		suffix = ""
	} else {
		suffix = "?"
	}

	return kotlinTypeNameFromGraphQLTypeName(node.NamedType) + suffix
}

func kotlinTypeNameFromGraphQLTypeName(graphQLTypeName string) string {
	if graphQLTypeName == "Int" {
		return "Int"
	} else if graphQLTypeName == "Float" {
		return "Float"
	} else if graphQLTypeName == "String" {
		return "String"
	} else if graphQLTypeName == "Boolean" {
		return "Boolean"
	} else if graphQLTypeName == "ID" {
		return "String"
	} else {
		return graphQLTypeName
	}
}

func isScalarType(node *ast.Type) bool {
	return isScalarTypeName(node.NamedType)
}

func isScalarTypeName(typeName string) bool {
	if typeName == "Int" {
		return true
	} else if typeName == "Float" {
		return true
	} else if typeName == "String" {
		return true
	} else if typeName == "Boolean" {
		return true
	} else if typeName == "ID" {
		return true
	} else {
		return false
	}
}

func flattenedVariablesFor(operation *ast.OperationDefinition, schema *ast.Schema) []*ast.VariableDefinition {
	flattenedVariables := make([]*ast.VariableDefinition, 0, 0)

	for _, variableDefinition := range operation.VariableDefinitions {
		if isScalarType(variableDefinition.Type) {
			flattenedVariables = append(flattenedVariables, variableDefinition)
			continue
		}

		childFlattenedVariables := flattenedVariablesForType(variableDefinition.Type, schema)
		pickedFieldDefinitions := pickedFieldsForVariableDefinition(variableDefinition)
		pickedFieldNames := fieldDefinitionByFieldNameMapFromFieldDefinitions(pickedFieldDefinitions)
		for _, childFlattenedVariable := range childFlattenedVariables {
			_, isChildFlattenedVariablePicked := pickedFieldNames[childFlattenedVariable.Variable]
			if isChildFlattenedVariablePicked {
				flattenedVariables = append(flattenedVariables, childFlattenedVariable)
			}
		}
	}

	return flattenedVariables
}

func flattenedVariablesForType(typeNode *ast.Type, schema *ast.Schema) []*ast.VariableDefinition {
	flattenedVariables := make([]*ast.VariableDefinition, 0, 0)

	typeInfo := schema.Types[typeNode.NamedType]
	for _, field := range typeInfo.Fields {
		if isScalarType(field.Type) {
			flattenedVariables = append(flattenedVariables, &ast.VariableDefinition{
				Variable: field.Name,
				Type:     field.Type,
			})
		} else {
			flattenedVariables = append(flattenedVariables, flattenedVariablesForType(field.Type, schema)...)
		}
	}

	return flattenedVariables
}

type fieldWithPickedSubFields struct {
	Field           *ast.FieldDefinition
	PickedSubFields map[string]*ast.FieldDefinition
}

type convenienceFunctionVariablesArgumentsRecursiveArg struct {
	OperationName string
	Schema        *ast.Schema
	Fields        []fieldWithPickedSubFields
}

func createConvenienceFunctionVariablesArgumentsRecursiveArgFromConfig(config RenderOperationTemplateConfig) convenienceFunctionVariablesArgumentsRecursiveArg {
	fields := make([]fieldWithPickedSubFields, 0, 0)

	for _, variableDefinition := range config.Operation.VariableDefinitions {
		fieldDefinition := fieldDefinitionFromVariableDefinition(variableDefinition)
		pickedSubFields := fieldDefinitionByFieldNameMapFromFieldDefinitions(pickedFieldsForVariableDefinition(variableDefinition))
		fields = append(fields, fieldWithPickedSubFields{
			Field:           fieldDefinition,
			PickedSubFields: pickedSubFields,
		})
	}

	return convenienceFunctionVariablesArgumentsRecursiveArg{
		OperationName: config.Operation.Name,
		Schema:        config.Schema,
		Fields:        fields,
	}
}

func createConvenienceFunctionVariablesArgumentsRecursiveArgFromArgAndType(arg convenienceFunctionVariablesArgumentsRecursiveArg, typeNode *ast.Type) convenienceFunctionVariablesArgumentsRecursiveArg {
	typeInfo := arg.Schema.Types[typeNode.NamedType]

	fields := make([]fieldWithPickedSubFields, 0, 0)
	for _, field := range typeInfo.Fields {
		fields = append(fields, fieldWithPickedSubFields{Field: field})
	}

	arg.Fields = fields
	return arg
}

func fieldDefinitionFromVariableDefinition(variableDefinition *ast.VariableDefinition) *ast.FieldDefinition {
	return &ast.FieldDefinition{
		Name:       variableDefinition.Variable,
		Type:       variableDefinition.Type,
		Directives: variableDefinition.Directives,
		Position:   variableDefinition.Position,
	}
}

func pickedFieldsForVariableDefinition(variableDefinition *ast.VariableDefinition) []*ast.FieldDefinition {
	pickDirective := pickDirectiveForVariableDefinition(variableDefinition)
	if pickDirective == nil {
		return variableDefinition.Definition.Fields
	}

	pickedFields := make(map[string]*ast.ChildValue)
	for _, pickDirectiveArgument := range pickDirective.Arguments {
		if pickDirectiveArgument.Name == "fields" {
			for _, pickDirectiveArgumentChildValue := range pickDirectiveArgument.Value.Children {
				pickedFields[pickDirectiveArgumentChildValue.Value.Raw] = pickDirectiveArgumentChildValue
			}
		}
	}

	fieldDefinitions := make([]*ast.FieldDefinition, 0, 0)
	for _, field := range variableDefinition.Definition.Fields {
		if _, isFieldPicked := pickedFields[field.Name]; isFieldPicked {
			fieldDefinitions = append(fieldDefinitions, field)
		}
	}

	return fieldDefinitions
}

func pickDirectiveForVariableDefinition(variableDefinition *ast.VariableDefinition) *ast.Directive {
	for _, directive := range variableDefinition.Directives {
		if directive.Name == "pick" {
			return directive
		}
	}
	return nil
}

func fieldDefinitionByFieldNameMapFromFieldDefinitions(fieldDefinitions []*ast.FieldDefinition) map[string]*ast.FieldDefinition {
	fieldDefinitionByFieldName := make(map[string]*ast.FieldDefinition)
	for _, fieldDefinition := range fieldDefinitions {
		fieldDefinitionByFieldName[fieldDefinition.Name] = fieldDefinition
	}
	return fieldDefinitionByFieldName
}

func fail(a ...any) (any, error) {
	return 42, errors.New(fmt.Sprint(a...))
}
