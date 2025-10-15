#!/bin/bash

# Comprehensive Location Tracking System Test Script
# This script automates the testing procedures outlined in COMPREHENSIVE_TESTING_CHECKLIST.md

echo "==================================================================="
echo "Alzheimer's Caregiver App - Location Tracking System Test Suite"
echo "==================================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0
TOTAL_TESTS=0

# Function to print test status
print_test_result() {
    local test_name="$1"
    local result="$2"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if [ "$result" = "PASS" ]; then
        echo -e "${GREEN}✅ $test_name: PASS${NC}"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}❌ $test_name: FAIL${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

# Function to check build status
check_build() {
    echo -e "${BLUE}Testing Build Configuration...${NC}"
    
    # Test patient app build
    echo "Building patient app..."
    cd "/c/Users/mihir/OneDrive/Desktop/temp/AlzheimersCaregiver"
    
    if ./gradlew :app:assembleDebug -x lint > build_test.log 2>&1; then
        print_test_result "Patient App Build" "PASS"
    else
        print_test_result "Patient App Build" "FAIL"
        echo -e "${RED}Build errors found. Check build_test.log for details.${NC}"
    fi
    
    # Test CaretakerApp build
    echo "Building CaretakerApp..."
    cd "CaretakerApp"
    
    if ./gradlew assembleDebug -x lint > ../caretaker_build_test.log 2>&1; then
        print_test_result "CaretakerApp Build" "PASS"
    else
        print_test_result "CaretakerApp Build" "FAIL"
        echo -e "${RED}CaretakerApp build errors found. Check caretaker_build_test.log for details.${NC}"
    fi
    
    cd ..
}

# Function to check configuration values
check_configuration() {
    echo -e "${BLUE}Testing Configuration Values...${NC}"
    
    # Check LocationConfig constants
    local config_file="app/src/main/java/com/mihir/alzheimerscaregiver/location/LocationConfig.java"
    
    if [ -f "$config_file" ]; then
        # Check MIN_UPLOAD_INTERVAL_MS = 5 minutes (300000ms)
        if grep -q "MIN_UPLOAD_INTERVAL_MS = 5 \* 60 \* 1000" "$config_file"; then
            print_test_result "MIN_UPLOAD_INTERVAL_MS Configuration" "PASS"
        else
            print_test_result "MIN_UPLOAD_INTERVAL_MS Configuration" "FAIL"
        fi
        
        # Check SMALLEST_DISPLACEMENT_METERS = 25.0f
        if grep -q "SMALLEST_DISPLACEMENT_METERS = 25.0f" "$config_file"; then
            print_test_result "SMALLEST_DISPLACEMENT_METERS Configuration" "PASS"
        else
            print_test_result "SMALLEST_DISPLACEMENT_METERS Configuration" "FAIL"
        fi
        
        # Check HISTORY_RETENTION_PER_DAY = 200
        if grep -q "HISTORY_RETENTION_PER_DAY = 200" "$config_file"; then
            print_test_result "HISTORY_RETENTION_PER_DAY Configuration" "PASS"
        else
            print_test_result "HISTORY_RETENTION_PER_DAY Configuration" "FAIL"
        fi
        
        # Check STALE_THRESHOLD_MS = 15 minutes (900000ms)
        if grep -q "STALE_THRESHOLD_MS = 15 \* 60 \* 1000" "$config_file"; then
            print_test_result "STALE_THRESHOLD_MS Configuration" "PASS"
        else
            print_test_result "STALE_THRESHOLD_MS Configuration" "FAIL"
        fi
    else
        print_test_result "LocationConfig File Exists" "FAIL"
    fi
}

# Function to check Android manifest permissions
check_permissions() {
    echo -e "${BLUE}Testing Android Permissions...${NC}"
    
    local manifest="app/src/main/AndroidManifest.xml"
    
    if [ -f "$manifest" ]; then
        # Check location permissions
        if grep -q "ACCESS_FINE_LOCATION" "$manifest"; then
            print_test_result "ACCESS_FINE_LOCATION Permission" "PASS"
        else
            print_test_result "ACCESS_FINE_LOCATION Permission" "FAIL"
        fi
        
        if grep -q "ACCESS_BACKGROUND_LOCATION" "$manifest"; then
            print_test_result "ACCESS_BACKGROUND_LOCATION Permission" "PASS"
        else
            print_test_result "ACCESS_BACKGROUND_LOCATION Permission" "FAIL"
        fi
        
        # Check foreground service permissions
        if grep -q "FOREGROUND_SERVICE_LOCATION" "$manifest"; then
            print_test_result "FOREGROUND_SERVICE_LOCATION Permission" "PASS"
        else
            print_test_result "FOREGROUND_SERVICE_LOCATION Permission" "FAIL"
        fi
        
        # Check service declaration
        if grep -q "PatientLocationService" "$manifest"; then
            print_test_result "PatientLocationService Declaration" "PASS"
        else
            print_test_result "PatientLocationService Declaration" "FAIL"
        fi
        
        # Check boot receiver
        if grep -q "LocationBootReceiver" "$manifest"; then
            print_test_result "LocationBootReceiver Declaration" "PASS"
        else
            print_test_result "LocationBootReceiver Declaration" "FAIL"
        fi
    else
        print_test_result "AndroidManifest.xml Exists" "FAIL"
    fi
}

# Function to check Firebase configuration
check_firebase_config() {
    echo -e "${BLUE}Testing Firebase Configuration...${NC}"
    
    # Check patient app google-services.json
    local patient_config="app/google-services.json"
    if [ -f "$patient_config" ]; then
        # Check database URL
        if grep -q "recallar-12588-default-rtdb.asia-southeast1.firebasedatabase.app" "$patient_config"; then
            print_test_result "Patient App Firebase Database URL" "PASS"
        else
            print_test_result "Patient App Firebase Database URL" "FAIL"
        fi
        
        # Check package name
        if grep -q "com.mihir.alzheimerscaregiver" "$patient_config"; then
            print_test_result "Patient App Package Name" "PASS"
        else
            print_test_result "Patient App Package Name" "FAIL"
        fi
    else
        print_test_result "Patient App google-services.json" "FAIL"
    fi
    
    # Check CaretakerApp google-services.json
    local caretaker_config="CaretakerApp/app/google-services.json"
    if [ -f "$caretaker_config" ]; then
        # Check database URL
        if grep -q "recallar-12588-default-rtdb.asia-southeast1.firebasedatabase.app" "$caretaker_config"; then
            print_test_result "CaretakerApp Firebase Database URL" "PASS"
        else
            print_test_result "CaretakerApp Firebase Database URL" "FAIL"
        fi
        
        # Check package name
        if grep -q "com.mihir.alzheimerscaregiver.caretaker" "$caretaker_config"; then
            print_test_result "CaretakerApp Package Name" "PASS"
        else
            print_test_result "CaretakerApp Package Name" "FAIL"
        fi
    else
        print_test_result "CaretakerApp google-services.json" "FAIL"
    fi
}

# Function to check documentation
check_documentation() {
    echo -e "${BLUE}Testing Documentation...${NC}"
    
    # Check troubleshooting guides
    if [ -f "FIREBASE_LOCATION_DATA_TROUBLESHOOTING.md" ]; then
        print_test_result "Firebase Troubleshooting Guide" "PASS"
    else
        print_test_result "Firebase Troubleshooting Guide" "FAIL"
    fi
    
    if [ -f "LOCATION_UPDATE_TROUBLESHOOTING.md" ]; then
        print_test_result "Location Update Troubleshooting Guide" "PASS"
    else
        print_test_result "Location Update Troubleshooting Guide" "FAIL"
    fi
    
    if [ -f "COMPREHENSIVE_TESTING_CHECKLIST.md" ]; then
        print_test_result "Comprehensive Testing Checklist" "PASS"
    else
        print_test_result "Comprehensive Testing Checklist" "FAIL"
    fi
    
    if [ -f "LOCATION_TRACKING_IMPLEMENTATION_SUMMARY.md" ]; then
        print_test_result "Implementation Summary" "PASS"
    else
        print_test_result "Implementation Summary" "FAIL"
    fi
}

# Function to check code quality
check_code_quality() {
    echo -e "${BLUE}Testing Code Quality...${NC}"
    
    # Check LocationConfig usage in PatientLocationService
    local service_file="app/src/main/java/com/mihir/alzheimerscaregiver/location/PatientLocationService.java"
    if [ -f "$service_file" ]; then
        if grep -q "LocationConfig\." "$service_file"; then
            print_test_result "LocationConfig Usage in PatientLocationService" "PASS"
        else
            print_test_result "LocationConfig Usage in PatientLocationService" "FAIL"
        fi
    fi
    
    # Check LocationBootReceiver exists and has proper implementation
    local boot_receiver="app/src/main/java/com/mihir/alzheimerscaregiver/location/LocationBootReceiver.java"
    if [ -f "$boot_receiver" ]; then
        if grep -q "syncWithFirebase" "$boot_receiver"; then
            print_test_result "LocationBootReceiver Firebase Sync" "PASS"
        else
            print_test_result "LocationBootReceiver Firebase Sync" "FAIL"
        fi
    else
        print_test_result "LocationBootReceiver File Exists" "FAIL"
    fi
    
    # Check debug functionality in TrackingActivity
    local tracking_activity="app/src/main/java/com/mihir/alzheimerscaregiver/TrackingActivity.java"
    if [ -f "$tracking_activity" ]; then
        if grep -q "debugFirebaseConnection" "$tracking_activity"; then
            print_test_result "Debug Functionality in TrackingActivity" "PASS"
        else
            print_test_result "Debug Functionality in TrackingActivity" "FAIL"
        fi
    else
        print_test_result "TrackingActivity File Exists" "FAIL"
    fi
}

# Function to validate test file structure
check_test_structure() {
    echo -e "${BLUE}Testing Test Structure...${NC}"
    
    # Check if test directories exist
    if [ -d "app/src/test/java" ]; then
        print_test_result "Unit Test Directory Structure" "PASS"
    else
        print_test_result "Unit Test Directory Structure" "FAIL"
    fi
    
    # Check for LocationUploaderTest
    if [ -f "app/src/test/java/com/mihir/alzheimerscaregiver/location/LocationUploaderTest.java" ]; then
        print_test_result "LocationUploaderTest File" "PASS"
    else
        print_test_result "LocationUploaderTest File" "FAIL"
    fi
}

# Function to generate test report
generate_report() {
    echo ""
    echo "==================================================================="
    echo -e "${BLUE}TEST EXECUTION SUMMARY${NC}"
    echo "==================================================================="
    echo "Date: $(date)"
    echo "Total Tests: $TOTAL_TESTS"
    echo -e "Tests Passed: ${GREEN}$TESTS_PASSED${NC}"
    echo -e "Tests Failed: ${RED}$TESTS_FAILED${NC}"
    
    if [ $TESTS_FAILED -eq 0 ]; then
        echo -e "${GREEN}✅ ALL TESTS PASSED!${NC}"
        echo "The location tracking system is properly configured and ready for manual testing."
    else
        echo -e "${RED}❌ SOME TESTS FAILED!${NC}"
        echo "Please review the failed tests and fix the issues before proceeding."
    fi
    
    echo ""
    echo "Next Steps:"
    echo "1. If all tests passed, proceed with manual testing using COMPREHENSIVE_TESTING_CHECKLIST.md"
    echo "2. Install apps on physical devices for location tracking validation"
    echo "3. Test Firebase real-time synchronization between patient and caretaker apps"
    echo "4. Validate battery usage and performance over extended periods"
    
    # Write report to file
    cat > TEST_EXECUTION_REPORT.md << EOF
# Test Execution Report

**Date:** $(date)  
**Total Tests:** $TOTAL_TESTS  
**Tests Passed:** $TESTS_PASSED  
**Tests Failed:** $TESTS_FAILED  

## Test Results Summary

$(if [ $TESTS_FAILED -eq 0 ]; then echo "✅ **ALL TESTS PASSED**"; else echo "❌ **SOME TESTS FAILED**"; fi)

## Automated Tests Completed

- Build Configuration Tests
- Configuration Values Validation  
- Android Permissions Check
- Firebase Configuration Validation
- Documentation Completeness
- Code Quality Assessment
- Test Structure Validation

## Next Steps

1. **Manual Testing**: Use \`COMPREHENSIVE_TESTING_CHECKLIST.md\` for device testing
2. **Device Installation**: Install on physical Android devices  
3. **Real-time Testing**: Test Firebase synchronization between apps
4. **Performance Testing**: Validate battery usage and long-term stability

## Files Generated

- \`build_test.log\` - Patient app build output
- \`caretaker_build_test.log\` - CaretakerApp build output  
- \`TEST_EXECUTION_REPORT.md\` - This report

For detailed manual testing procedures, see \`COMPREHENSIVE_TESTING_CHECKLIST.md\`.
EOF

    echo ""
    echo "Test report saved to: TEST_EXECUTION_REPORT.md"
}

# Main execution
main() {
    echo "Starting automated test suite..."
    echo ""
    
    # Run test suites
    check_build
    echo ""
    
    check_configuration
    echo ""
    
    check_permissions
    echo ""
    
    check_firebase_config
    echo ""
    
    check_documentation
    echo ""
    
    check_code_quality
    echo ""
    
    check_test_structure
    echo ""
    
    # Generate final report
    generate_report
}

# Execute main function
main "$@"